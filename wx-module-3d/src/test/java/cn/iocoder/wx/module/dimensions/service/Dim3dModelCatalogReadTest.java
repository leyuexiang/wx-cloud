package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.service.impl.Dim3dResourceCatalogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用实际查询验证公共目录、账号隔离和分页条件，避免保存成功后被旧租户条件隐藏。 */
class Dim3dModelCatalogReadTest {
    private JdbcTemplate jdbc;
    private ArtResourceAccess access;
    private Dim3dResourceCatalogServiceImpl service;

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE wx_3d_model(id BIGINT PRIMARY KEY,model_name VARCHAR(50),"
                + "category VARCHAR(32),resource_belong VARCHAR(16),tenant_id VARCHAR(20),"
                + "data_user_id BIGINT,create_by BIGINT,status VARCHAR(16),is_paid VARCHAR(16),del_flag CHAR)");
        jdbc.update("INSERT INTO wx_3d_model VALUES"
                + "(1,'公共树','TREE','SYSTEM','0',9001,9001,'ONLINE','FREE','0'),"
                + "(2,'个人树','TREE','MINE','41',7001,7001,'ONLINE','FREE','0'),"
                + "(3,'其他账号','TREE','MINE','41',7002,7002,'ONLINE','FREE','0'),"
                + "(4,'其他租户','TREE','MINE','42',7001,7001,'ONLINE','FREE','0'),"
                + "(5,'已删除','TREE','SYSTEM','0',9001,9001,'ONLINE','FREE','1'),"
                + "(6,'历史个人','BUILDING','MINE','41',NULL,7001,'OFFLINE','FREE','0'),"
                + "(7,'未知所有者','TREE','MINE','41',NULL,NULL,'ONLINE','FREE','0'),"
                + "(8,'非公共租户','TREE','SYSTEM','42',9001,9001,'ONLINE','FREE','0'),"
                + "(9,'下线系统模型','TREE','SYSTEM','0',9001,9001,'OFFLINE','FREE','0')");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        service = new Dim3dResourceCatalogServiceImpl();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "access", access);
    }

    @AfterEach
    void release() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void listsGlobalAndOwnAccountIncludingLegacyOwner() {
        var result = service.getModelPage(null, null, null, null, null, 1, 20);
        assertEquals(3, result.getTotal());
        assertEquals(List.of(6L, 2L, 1L), result.getList().stream().map(Dim3dModel::getId).toList());
    }

    @Test
    void sharesSystemAcrossTenantsAndSeparatesAccounts() {
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(8001, 42));
        assertEquals(1, service.getModelPage(null, "SYSTEM", null, null, null, 1, 10).getTotal());
        assertEquals(0, service.getModelPage(null, "MINE", null, null, null, 1, 10).getTotal());
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7002, 41));
        assertEquals(List.of(3L), service.getModelPage(null, "MINE", null, null, null, 1, 10)
                .getList().stream().map(Dim3dModel::getId).toList());
    }

    @Test
    void appliesFiltersAndKeepsPageTotalConsistent() {
        var result = service.getModelPage("树", null, "ONLINE", "FREE", "TREE", 2, 1);
        assertEquals(2, result.getTotal());
        assertEquals(List.of(1L), result.getList().stream().map(Dim3dModel::getId).toList());
    }

    /** 租户查询下线状态时，计数和结果均排除下线系统模型并保留自己的下线模型。 */
    @Test
    void tenantCannotListOrReadOfflineSystemModels() {
        var offline = service.getModelPage(null, null, "OFFLINE", null, null, 1, 10);
        assertEquals(1, offline.getTotal());
        assertEquals(List.of(6L), offline.getList().stream().map(Dim3dModel::getId).toList());
        assertEquals(0, service.getModelPage(null, "SYSTEM", "OFFLINE", null, null, 1, 10).getTotal());
        assertThrows(ServiceException.class, () -> service.getModel(9L));
    }

    /** 运营维护仍能查看系统下线记录，同时保留个人模型的租户与账号隔离。 */
    @Test
    void platformMaintainerCanListAndReadOfflineSystemModels() {
        var operator = new ArtResourceAccess.Actor(9001, 1);
        when(access.actor()).thenReturn(operator);
        when(access.isPlatformAdministrator(operator)).thenReturn(true);
        var offline = service.getModelPage(null, "SYSTEM", "OFFLINE", null, null, 1, 10);
        assertEquals(1, offline.getTotal());
        assertEquals(List.of(9L), offline.getList().stream().map(Dim3dModel::getId).toList());
        assertEquals(9L, service.getModel(9L).getId());
        assertThrows(ServiceException.class, () -> service.getModel(6L));
    }

    @Test
    void detailUsesSameVisibilityAsList() {
        assertEquals(1L, service.getModel(1L).getId());
        assertEquals(2L, service.getModel(2L).getId());
        assertThrows(ServiceException.class, () -> service.getModel(3L));
        assertThrows(ServiceException.class, () -> service.getModel(4L));
        assertThrows(ServiceException.class, () -> service.getModel(5L));
        assertThrows(ServiceException.class, () -> service.getModel(7L));
    }

    /** 排序只使用固定字段映射，分页还需追加主键保证相同值的顺序稳定。 */
    @Test
    void acceptsContractSortAndRejectsSqlFragments() {
        var result = service.getModelPage(null, null, null, null, null, 1, 20, "id", "asc");
        assertEquals(List.of(1L, 2L, 6L), result.getList().stream().map(Dim3dModel::getId).toList());
        assertThrows(ServiceException.class, () -> service.getModelPage(null, null, null, null, null,
                1, 20, "id; DROP TABLE wx_3d_model", "asc"));
        assertThrows(ServiceException.class, () -> service.getModelPage(null, null, null, null, null,
                1, 20, "id", "other"));
    }
}
