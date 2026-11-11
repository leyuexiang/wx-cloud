package cn.iocoder.wx.module.infra.controller.admin.file;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.OwnedFileRespVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.wx.module.infra.service.file.FileService;
import cn.iocoder.wx.module.infra.service.file.OwnedImageUploadResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 公共图片文件接口只暴露本地文件编号与短期地址，读取授权交由 Service 强制复核。 */
class OwnedImageControllerTest {

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
