package cn.iocoder.wx.module.infra.service.file;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.object.ObjectUtils;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import org.springframework.transaction.PlatformTransactionManager;
import cn.iocoder.wx.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.wx.framework.test.core.util.AssertUtils;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.wx.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.wx.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;

import static cn.iocoder.wx.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.wx.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.wx.framework.test.core.util.RandomUtils.*;
import static cn.iocoder.wx.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@Import({FileServiceImpl.class})
public class FileServiceImplTest extends BaseDbUnitTest {

    @Resource
    private FileService fileService;

    @Resource
    private FileMapper fileMapper;

    @MockBean
    private FileConfigService fileConfigService;

    @MockBean
    private SecurityFrameworkService securityFrameworkService;

    @MockBean
    private PlatformTransactionManager transactionManager;

    @Test
    public void testGetFilePage() {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> { // 等会查询到
            o.setPath("yunai");
            o.setType("image/jpg");
            o.setCreateTime(buildTime(2021, 1, 15));
        });
        fileMapper.insert(dbFile);
        // 测试 path 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> o.setPath("tudou")));
        // 测试 type 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> {
            o.setType("image/png");
        }));
        // 测试 createTime 不匹配
        fileMapper.insert(ObjectUtils.cloneIgnoreId(dbFile, o -> {
            o.setCreateTime(buildTime(2020, 1, 15));
        }));
        // 准备参数
        FilePageReqVO reqVO = new FilePageReqVO();
        reqVO.setPath("yunai");
        reqVO.setType("jp");
        reqVO.setCreateTime((new LocalDateTime[]{buildTime(2021, 1, 10), buildTime(2021, 1, 20)}));

        // 调用
        PageResult<FileDO> pageResult = fileService.getFilePage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        AssertUtils.assertPojoEquals(dbFile, pageResult.getList().get(0));
    }

    @Test
    public void testCreateFile_success() throws Exception {
        // 准备参数
        String path = randomString();
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        // mock Master 文件客户端
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        String url = randomString();
        when(client.upload(same(content), same(path), eq("image/jpeg"))).thenReturn(url);
        when(client.getId()).thenReturn(10L);
        String name = "单测文件名";
        // 调用
        String result = fileService.createFile(name, path, content);
        // 断言
        assertEquals(result, url);
        // 校验数据
        FileDO file = fileMapper.selectOne(FileDO::getPath, path);
        assertEquals(10L, file.getConfigId());
        assertEquals(path, file.getPath());
        assertEquals(url, file.getUrl());
        assertEquals("image/jpeg", file.getType());
        assertEquals(content.length, file.getSize());
    }

    @org.junit.jupiter.api.AfterEach
    public void clearAuthContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    public void testCreateOwnedImage_recordsTrustedOwnerAndGeneratedIdentity() throws Exception {
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        FileClient client = mock(FileClient.class);
        when(securityFrameworkService.hasAnyPermissions(any(String[].class))).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation ->
                new org.springframework.transaction.support.SimpleTransactionStatus());
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(17L);
        when(securityFrameworkService.hasPermission("infra:file:query")).thenReturn(true);
        when(fileConfigService.getFileClient(17L)).thenReturn(client);
        when(client.upload(any(byte[].class), anyString(), eq("image/jpeg"))).thenAnswer(invocation -> "https://legacy/" + invocation.getArgument(1));
        when(client.getPresignedDownloadUrl(anyString(), eq(600))).thenAnswer(invocation -> "https://signed/" + invocation.getArgument(0));
        setActor(41L, 7001L);

        // 上传回执同时持有元数据和本次读取地址，文件身份从真实插入结果取得。
        OwnedImageUploadResult result = fileService.createOwnedImage("访客图片.jpg", content);
        FileDO created = result.file();

        assertNotNull(created.getId());
        assertEquals(17L, created.getConfigId());
        assertEquals(41L, created.getTenantId());
        assertEquals(7001L, created.getUploaderId());
        assertEquals("PRIVATE_UPLOAD", created.getVisibilityScope());
        assertTrue(created.getPath().matches("images/[0-9a-f]{32}\\.jpg"));
        assertEquals("https://signed/" + created.getPath(),
                fileService.getOwnedFileReadUrl(created));
        verify(client, times(2)).getPresignedDownloadUrl(created.getPath(), 600);
        assertEquals("image/jpeg", created.getType());
        assertEquals(content.length, created.getSize());
        assertEquals(created.getId(), fileMapper.selectById(created.getId()).getId());
    }

    @Test
    public void testCreateOwnedImage_cleansObjectWhenReadSigningUnavailable() throws Exception {
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        FileClient client = mock(FileClient.class);
        when(securityFrameworkService.hasAnyPermissions(any(String[].class))).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenAnswer(invocation ->
                new org.springframework.transaction.support.SimpleTransactionStatus());
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.upload(same(content), anyString(), eq("image/jpeg"))).thenReturn("https://files.example.test/object");
        when(client.getPresignedDownloadUrl(anyString(), eq(600))).thenThrow(new UnsupportedOperationException("GET signing unavailable"));
        when(client.getId()).thenReturn(19L);
        setActor(41L, 7001L);

        assertThrows(RuntimeException.class, () -> fileService.createOwnedImage("image.jpg", content));
        org.mockito.ArgumentCaptor<String> path = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).upload(same(content), path.capture(), eq("image/jpeg"));
        verify(client).getPresignedDownloadUrl(path.getValue(), 600);
        assertEquals(0, fileMapper.selectCount());
        verify(client).delete(path.getValue());
        verifyNoMoreInteractions(client);
    }

    /** 存储 SDK 的受检异常同样必须回收本次唯一对象。 */
    @Test
    public void testCreateOwnedImage_cleansObjectWhenReadSigningThrowsCheckedException() throws Exception {
        FileClient client = mock(FileClient.class);
        when(securityFrameworkService.hasAnyPermissions(any(String[].class))).thenReturn(true);
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getPresignedDownloadUrl(anyString(), eq(600)))
                .thenThrow(new java.io.IOException("签名服务不可用"));
        setActor(41L, 7001L);

        var failure = assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class,
                () -> fileService.createOwnedImage("图片.jpg", ResourceUtil.readBytes("file/erweima.jpg")));

        assertEquals(503, failure.getCode());
        verify(client).delete(anyString());
        assertEquals(0, fileMapper.selectCount());
    }

    @Test
    public void testGetOwnedFile_checksTrustedTenantUploaderAndLegacyBoundary() {
        FileDO owned = randomPojo(FileDO.class, o -> o.setTenantId(41L).setUploaderId(7001L)
                .setVisibilityScope("PRIVATE_UPLOAD"));
        FileDO legacy = randomPojo(FileDO.class, o -> o.setTenantId(null).setUploaderId(null)
                .setVisibilityScope(null));
        fileMapper.insert(owned);
        fileMapper.insert(legacy);
        setActor(41L, 7001L);
        when(securityFrameworkService.hasPermission("infra:file:query")).thenReturn(true);
        assertNotNull(fileService.getOwnedFile(owned.getId()));
        assertNull(fileService.getOwnedFile(legacy.getId()));
        setActor(42L, 7001L);
        assertNull(fileService.getOwnedFile(owned.getId()));
        setActor(41L, 7002L);
        assertNull(fileService.getOwnedFile(owned.getId()));
    }

    @Test
    public void testCreateOwnedImage_requiresAuthenticatedTenant() {
        byte[] content = ResourceUtil.readBytes("file/erweima.jpg");
        assertThrows(RuntimeException.class, () -> fileService.createOwnedImage("图标.jpg", content));
        verifyNoInteractions(fileConfigService);
        verify(securityFrameworkService, never()).hasAnyPermissions(any(String[].class));
    }

    private static void setActor(long tenantId, long userId) {
        LoginUser actor = new LoginUser();
        actor.setId(userId);
        actor.setTenantId(tenantId);
        TenantContextHolder.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of()));
    }

    /** 历史匿名文件读取端点也必须遵守新图片的归属，防止绕过签名有效期。 */
    @Test
    public void testGetFileContent_rechecksOwnedImageVisibility() throws Exception {
        FileDO file = randomPojo(FileDO.class, value -> value.setConfigId(17L).setPath("images/owned.png")
                .setTenantId(41L).setUploaderId(7001L).setVisibilityScope("PRIVATE_UPLOAD"));
        fileMapper.insert(file);
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(17L)).thenReturn(client);
        when(client.getContent(file.getPath())).thenReturn(new byte[]{1, 2});
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class,
                () -> fileService.getFileContent(17L, file.getPath()));
        setActor(41L, 7002L);
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class,
                () -> fileService.getFileContent(17L, file.getPath()));
        setActor(41L, 7001L);
        assertArrayEquals(new byte[]{1, 2}, fileService.getFileContent(17L, file.getPath()));
        verify(client, times(1)).getContent(file.getPath());
    }

    @Test
    public void testDeleteFile_success() throws Exception {
        // mock 数据
        FileDO dbFile = randomPojo(FileDO.class, o -> o.setConfigId(10L).setPath("tudou.jpg"));
        fileMapper.insert(dbFile);// @Sql: 先插入出一条存在的数据
        // mock Master 文件客户端
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(eq(10L))).thenReturn(client);
        // 准备参数
        Long id = dbFile.getId();

        // 调用
        fileService.deleteFile(id);
        // 校验数据不存在了
        assertNull(fileMapper.selectById(id));
        // 校验调用
        verify(client).delete(eq("tudou.jpg"));
    }

    @Test
    public void testDeleteFile_notExists() {
        // 准备参数
        Long id = randomLongId();

        // 调用, 并断言异常
        assertServiceException(() -> fileService.deleteFile(id), FILE_NOT_EXISTS);
    }

    @Test
    public void testGetFileContent() throws Exception {
        // 准备参数
        Long configId = 10L;
        String path = "tudou.jpg";
        // mock 方法
        FileClient client = mock(FileClient.class);
        when(fileConfigService.getFileClient(eq(10L))).thenReturn(client);
        byte[] content = new byte[]{};
        when(client.getContent(eq("tudou.jpg"))).thenReturn(content);

        // 调用
        byte[] result = fileService.getFileContent(configId, path);
        // 断言
        assertSame(result, content);
    }

}
