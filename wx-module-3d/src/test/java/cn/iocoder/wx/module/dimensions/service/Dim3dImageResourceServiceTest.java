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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过真实数据库事务验证图片目录和文件可见范围的共同提交及失败恢复。 */
class Dim3dImageResourceServiceTest {
    private JdbcTemplate jdbc;
    private Dim3dImageResourceService service;
    private ArtResourceAccess access;
    private SecurityFrameworkService security;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        // 内存库保留到当前用例结束，关闭连接后仍可参与下一条事务检查。
        source.setUrl(source.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE wx_infra_file(id BIGINT PRIMARY KEY,tenant_id BIGINT,uploader_id BIGINT,visibility_scope VARCHAR(20),type VARCHAR(40),size INT,deleted BOOLEAN DEFAULT FALSE,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE wx_3d_icon(id BIGINT PRIMARY KEY,icon_name VARCHAR(50),icon_desc VARCHAR(500),category VARCHAR(32),secondary_category VARCHAR(32),resource_belong VARCHAR(16),icon_file_id BIGINT,status VARCHAR(16),data_user_id BIGINT,tenant_id VARCHAR(20),create_by BIGINT,update_by BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,del_flag CHAR DEFAULT '0',resource_id BIGINT)");
        jdbc.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_scope VARCHAR(32),content_state VARCHAR(16))");
        jdbc.update("INSERT INTO wx_infra_file(id,tenant_id,uploader_id,visibility_scope,type,size,deleted) VALUES(5,41,7001,'PRIVATE_UPLOAD','image/png',50,FALSE)");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        var dict = mock(DictDataApi.class);
        when(dict.validateDictDataList(eq("dim3d_icon_category"), anyCollection()))
                .thenReturn(CommonResult.success(true));
        security = mock(SecurityFrameworkService.class);
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
        assertEquals(1, service.page(null, null, null, null, null, 1, 10, null, null).getTotal());
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
        assertEquals(1, service.page(null, "DEVICE_ICON", null, "SYSTEM", "ONLINE", 1, 10, null, null).getTotal());

        jdbc.update("UPDATE wx_3d_icon SET resource_belong='MINE',tenant_id='41' WHERE id=?", id);
        assertThrows(ServiceException.class, () -> service.get(id));
        assertEquals(0, service.page(null, null, null, null, null, 1, 10, null, null).getTotal());
    }

    /** 下线系统图片不进入租户的总数、分页或详情，同租户图片仍按状态查询。 */
    @Test
    void tenantCatalogExcludesOfflineSystemImages() {
        jdbc.update("""
                INSERT INTO wx_3d_icon(id,icon_name,resource_belong,tenant_id,status)
                VALUES (10,'系统已下线','SYSTEM','0','OFFLINE'),
                       (20,'租户已下线','MINE','41','OFFLINE')
                """);
        var page = service.page(null, null, null, null, "OFFLINE", 1, 10, null, null);
        assertEquals(1, page.getTotal());
        assertEquals(List.of(20L), page.getList().stream().map(Dim3dIcon::getId).toList());
        assertThrows(ServiceException.class, () -> service.get(10L));
        // 运营能查到已下线系统图标，维护上线后租户也可再次读取。
        var operator = new ArtResourceAccess.Actor(9001, 1);
        when(access.actor()).thenReturn(operator);
        when(access.isPlatformAdministrator(operator)).thenReturn(true);
        assertEquals(1, service.page(null, null, null, "SYSTEM", "OFFLINE", 1, 10, null, null).getTotal());
        assertEquals(10L, service.get(10L).getId());
        service.changeStatus(10L, "ONLINE");
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        assertEquals(10L, service.get(10L).getId());
    }

    /** 同值按编号倒序，跨租户记录不进入计数或分页。 */
    @Test
    void sortsFilteredPageWithStableTiesAndRejectsUnsupportedOrdering() {
        jdbc.update("""
                INSERT INTO wx_3d_icon(id,icon_name,category,secondary_category,resource_belong,tenant_id,status)
                VALUES (10,'A','DEVICE_ICON','室内','MINE','41','ONLINE'),
                       (20,'A','DEVICE_ICON','室内','MINE','41','ONLINE'),
                       (30,'B','DEVICE_ICON','室内','MINE','41','ONLINE'),
                       (40,'A','DEVICE_ICON','室内','MINE','42','ONLINE')
                """);
        var first = service.page(null, "DEVICE_ICON", "室内", "MINE", "ONLINE", 1, 2, "iconName", "ASC");
        var second = service.page(null, "DEVICE_ICON", "室内", "MINE", "ONLINE", 2, 2, "iconName", "ASC");
        assertEquals(3, first.getTotal());
        assertEquals(List.of(20L, 10L), first.getList().stream().map(Dim3dIcon::getId).toList());
        assertEquals(List.of(30L), second.getList().stream().map(Dim3dIcon::getId).toList());
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.page(null, null, null, null, null, 1, 10, "tenantId", "asc")).getCode());
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.page(null, null, null, null, null, 1, 10, "id", "ascending")).getCode());
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

    /** 同状态重放须保留上一位操作人和原更新时间。 */
    @Test
    void repeatedStateChangePreservesAudit() {
        long id = service.create(image());
        service.changeStatus(id, "OFFLINE");
        jdbc.update("UPDATE wx_3d_icon SET update_by=7002,update_time='2026-11-16 12:00:00' WHERE id=?", id);
        Dim3dIcon previous = service.get(id);

        service.changeStatus(id, "OFFLINE");

        Dim3dIcon current = service.get(id);
        assertEquals("OFFLINE", current.getStatus());
        assertEquals(previous.getUpdateBy(), current.getUpdateBy());
        assertEquals(previous.getUpdateTime(), current.getUpdateTime());
    }

    /** 状态重放同样受编辑权限及租户范围约束，受管关联继续走原有保护。 */
    @Test
    void statusRejectsUnsupportedStateAndUnauthorizedOrManagedRows() {
        long id = service.create(image());
        when(security.hasPermission("dim3d:icon:edit")).thenReturn(false);
        assertEquals(403, assertThrows(ServiceException.class, () -> service.changeStatus(id, "ONLINE")).getCode());
        when(security.hasPermission("dim3d:icon:edit")).thenReturn(true);
        assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(id, "offline")).getCode());

        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(8001, 42));
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "ONLINE")).getCode());
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        jdbc.update("INSERT INTO wx_3d_resource(id,resource_scope,content_state) VALUES(77,'TENANT_SHARED','READY')");
        jdbc.update("UPDATE wx_3d_icon SET resource_id=77 WHERE id=?", id);
        assertEquals(cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.NOT_VISIBLE.getCode(),
                assertThrows(ServiceException.class, () -> service.changeStatus(id, "OFFLINE")).getCode());
        assertEquals("ONLINE", service.get(id).getStatus());
    }

    /** 删除目录保留已绑定文件供场景等消费者读取。 */
    @Test
    void deleteHidesDirectoryAndRetainsBoundFile() {
        long id = service.create(image());
        service.delete(id);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.get(id)).getCode());
        assertEquals(404, assertThrows(ServiceException.class, () -> service.changeStatus(id, "ONLINE")).getCode());
        assertEquals("TENANT", jdbc.queryForObject("SELECT visibility_scope FROM wx_infra_file WHERE id=5", String.class));
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
