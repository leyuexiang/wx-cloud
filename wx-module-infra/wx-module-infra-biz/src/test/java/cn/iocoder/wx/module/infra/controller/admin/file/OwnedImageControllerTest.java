package cn.iocoder.wx.module.infra.controller.admin.file;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.OwnedFileRespVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.wx.module.infra.service.file.FileService;
import cn.iocoder.wx.module.infra.service.file.FileServiceImpl;
import cn.iocoder.wx.module.infra.service.file.OwnedImageUploadResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 公共图片文件接口只暴露本地文件编号与短期地址，读取授权交由 Service 强制复核。 */
class OwnedImageControllerTest {

    /** 用真实身份解析核对双端写入及查询，跨端请求不会开始读流或查文件。 */
    @Test
    void scopedUploadsAndReadsRequireTheMatchingPlatform() throws Exception {
        var service = spy(new FileServiceImpl());
        ReflectionTestUtils.setField(service, "platformTenant", 1L);
        doReturn(null).when(service).getOwnedFile(7L);
        var file = new FileDO();
        file.setId(75L);
        doReturn(new OwnedImageUploadResult(file, "https://storage.invalid/image"))
                .when(service).createOwnedImage(eq("image.png"), any());
        var mvc = MockMvcBuilders.standaloneSetup(controller(service))
                .setControllerAdvice(new GlobalExceptionHandler("infra-test", null)).build();
        String tenant = "/infra/file/tenant/resource/oss";
        String operation = "/infra/file/operation/resource/oss";
        var image = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        try {
            login(2L);
            mvc.perform(get(tenant + "/listByIds/7")).andExpect(jsonPath("$.code").value(200));
            mvc.perform(multipart(operation + "/upload").file(image).header("tenant-id", "1"))
                    .andExpect(jsonPath("$.code").value(403));
            verify(service, never()).createOwnedImage(any(), any());
            login(1L);
            mvc.perform(multipart(operation + "/upload").file(image)).andExpect(jsonPath("$.data.ossId").value("75"));
            mvc.perform(get(tenant + "/listByIds/8")).andExpect(jsonPath("$.code").value(403));
            verify(service, never()).getOwnedFile(8L);
            mvc.perform(get("/infra/file/resource/oss/listByIds/7")).andExpect(jsonPath("$.code").value(200));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
    }

    private static void login(long tenant) {
        var user = new LoginUser();
        user.setId(10L);
        user.setTenantId(tenant);
        TenantContextHolder.setTenantId(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void uploadReturnsStringIdAndShortAddress() throws Exception {
        FileService service = mock(FileService.class);
        FileDO file = new FileDO();
        file.setId(Long.MAX_VALUE - 5000L);
        file.setName("设备.png");
        file.setPath("images/unique-object-key.png");
        when(service.createOwnedImage(eq("C:\\fakepath\\设备.png"), any(byte[].class)))
                .thenReturn(new OwnedImageUploadResult(file, "https://signed.example.test/image"));
        OwnedImageController controller = controller(service);

        CommonResult<OwnedFileRespVO> response = controller.upload(
                new MockMultipartFile("file", "C:\\fakepath\\设备.png", "image/png", new byte[]{1, 2, 3}));

        assertEquals(String.valueOf(Long.MAX_VALUE - 5000L), response.getData().getOssId());
        assertEquals("https://signed.example.test/image", response.getData().getUrl());
        assertEquals("设备.png", response.getData().getOriginalName());
        assertEquals("png", response.getData().getFileSuffix());
        verify(service).createOwnedImage(eq("C:\\fakepath\\设备.png"), any(byte[].class));
    }

    @Test
    void rejectsMalformedAndOverflowIdsBeforeServiceAccess() throws Exception {
        FileService service = mock(FileService.class);
        OwnedImageController controller = controller(service);
        assertThrows(ServiceException.class, () -> controller.listById("1,2"));
        assertThrows(ServiceException.class, () -> controller.listById("9223372036854775808"));
        assertThrows(ServiceException.class, () -> controller.listById("-1"));
        verifyNoInteractions(service);
    }

    @Test
    void invisibleFileProducesEmptyListAndDoesNotSignUrl() throws Exception {
        FileService service = mock(FileService.class);
        when(service.getOwnedFile(75L)).thenReturn(null);
        CommonResult<List<OwnedFileRespVO>> result = controller(service).listById("75");
        assertNotNull(result.getData());
        assertTrue(result.getData().isEmpty());
        verify(service).getOwnedFile(75L);
        verify(service, never()).getOwnedFileReadUrl(any());
    }

    @Test
    void rejectsEmptyAndOversizedMultipartBeforeServiceAccess() {
        FileService service = mock(FileService.class);
        OwnedImageController controller = controller(service);
        assertThrows(ServiceException.class, () -> controller.upload(
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0])));
        assertThrows(ServiceException.class, () -> controller.upload(
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])));
        verifyNoInteractions(service);
    }

    private static OwnedImageController controller(FileService service) {
        OwnedImageController controller = new OwnedImageController();
        ReflectionTestUtils.setField(controller, "fileService", service);
        return controller;
    }
}
