package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过真实数据库事务验证图片目录和文件可见范围的共同提交及失败恢复。 */
class Dim3dImageResourceServiceTest {
    private JdbcTemplate jdbc;
    private Dim3dImageResourceService service;
    private ArtResourceAccess access;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        // 内存库保留到当前用例结束，关闭连接后仍可参与下一条事务检查。
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE wx_infra_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,uploader_id BIGINT,visibility_scope VARCHAR(20),type VARCHAR(40),size INT,deleted BOOLEAN DEFAULT FALSE,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE wx_3d_icon(id BIGINT PRIMARY KEY,icon_name VARCHAR(50),icon_desc VARCHAR(500),category VARCHAR(32),secondary_category VARCHAR(32),resource_belong VARCHAR(16),icon_file_id BIGINT,status VARCHAR(16),data_user_id BIGINT,tenant_id VARCHAR(20),create_by BIGINT,update_by BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,del_flag CHAR DEFAULT '0')");
        jdbc.update("INSERT INTO wx_infra_file(id,tenant_id,uploader_id,visibility_scope,type,size,deleted) VALUES(5,41,7001,'PRIVATE_UPLOAD','image/png',50,FALSE)");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        var dict = mock(DictDataApi.class);
        when(dict.validateDictDataList(eq("dim3d_icon_category"), anyCollection()))
                .thenReturn(CommonResult.success(true));
        var security = mock(SecurityFrameworkService.class);
        when(security.hasPermission(anyString())).thenReturn(true);
        service = new Dim3dImageResourceService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "transactions", new DataSourceTransactionManager(source));
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "dictionaries", dict);
        ReflectionTestUtils.setField(service, "security", security);
    }

    @AfterEach
    void releaseDatabase() {
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void createsDirectoryAndBindsFileToTrustedTenant() {
        Dim3dIcon request = image();
        request.setTenantId("99");
        request.setCreateBy(99L);
        long id = service.create(request);

        Dim3dIcon saved = service.get(id);
        assertEquals("41", saved.getTenantId());
        assertEquals(7001L, saved.getCreateBy());
        assertEquals("图", saved.getIconName());
        assertEquals("TENANT", jdbc.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=5", String.class));
        assertEquals(1, service.page(null, null, null, null, null, 1, 10).getTotal());
    }

    @Test
    void restoresPrivateFileWhenDirectoryInsertFails() {
        jdbc.execute("ALTER TABLE wx_3d_icon ADD CONSTRAINT reject_insert CHECK (icon_name <> '图')");
        assertThrows(RuntimeException.class, () -> service.create(image()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_icon", Integer.class));
        assertEquals("PRIVATE_UPLOAD", jdbc.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=5", String.class));
    }

    @Test
    void deniesAnotherUploaderAndUnprivilegedSystemWrite() {
        jdbc.update("UPDATE wx_infra_file SET uploader_id=7002 WHERE id=5");
        assertThrows(ServiceException.class, () -> service.create(image()));
        jdbc.update("UPDATE wx_infra_file SET uploader_id=7001 WHERE id=5");
        doThrow(new ServiceException(403, "无权维护系统资源")).when(access).maintain(any(), eq("GLOBAL_SYSTEM"), eq("0"));
        Dim3dIcon system = image();
        system.setResourceBelong("SYSTEM");
        assertThrows(ServiceException.class, () -> service.create(system));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_icon", Integer.class));
    }

    @Test
    void readsGlobalImagesAcrossTenantsAndKeepsPrivateTenantIsolated() {
        Dim3dIcon global = image();
        global.setResourceBelong("SYSTEM");
        long id = service.create(global);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(8001, 42));
        assertEquals(id, service.get(id).getId());
        assertEquals(1, service.page(null, "DEVICE_ICON", null, "SYSTEM", "ONLINE", 1, 10).getTotal());

        jdbc.update("UPDATE wx_3d_icon SET resource_belong='MINE',tenant_id='41' WHERE id=?", id);
        assertThrows(ServiceException.class, () -> service.get(id));
        assertEquals(0, service.page(null, null, null, null, null, 1, 10).getTotal());
    }

    @Test
    void restoresOriginalReferenceAndNewUploadWhenReplacementFails() {
        long id = service.create(image());
        jdbc.update("INSERT INTO wx_infra_file(id,tenant_id,uploader_id,visibility_scope,type,size,deleted) VALUES(6,41,7001,'PRIVATE_UPLOAD','image/png',70,FALSE)");
        jdbc.execute("ALTER TABLE wx_3d_icon ADD CONSTRAINT reject_update CHECK (icon_name <> '坏')");
        Dim3dIcon replacement = image();
        replacement.setIconName("坏");
        replacement.setIconFileId(6L);
        assertThrows(RuntimeException.class, () -> service.update(id, replacement));
        assertEquals(5L, service.get(id).getIconFileId());
        assertEquals("PRIVATE_UPLOAD", jdbc.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=6", String.class));
    }

    private static Dim3dIcon image() {
        Dim3dIcon body = new Dim3dIcon();
        body.setIconName(" 图 ");
        body.setCategory("DEVICE_ICON");
        body.setResourceBelong("MINE");
        body.setIconFileId(5L);
        return body;
    }
}
