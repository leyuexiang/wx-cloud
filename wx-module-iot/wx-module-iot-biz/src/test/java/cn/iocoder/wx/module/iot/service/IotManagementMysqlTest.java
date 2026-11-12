package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.tenant.config.TenantProperties;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO;
import cn.iocoder.wx.module.iot.dal.mysql.IotDeviceBindingMapper;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import cn.iocoder.wx.framework.mybatis.core.handler.DefaultDBFieldHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅在一次性独立MySQL容器运行：真实DDL、Mapper、租户插件、分页及JSON列，不触碰共享业务库。 */
@EnabledIfEnvironmentVariable(named = "IOT_MYSQL_TEST_URL", matches = ".+")
class IotManagementMysqlTest {
    private SqlSession session;
    private IotDeviceBindingServiceImpl service;
    private IotConnectorMapper connectors;

    @BeforeEach
    void prepare() throws Exception {
        String url = System.getenv("IOT_MYSQL_TEST_URL");
        assertTrue(url.contains("/iot_m2_check?"), "仅允许隔离测试库");
        String password = System.getenv("IOT_MYSQL_TEST_PASSWORD");
        try (var connection = DriverManager.getConnection(url, "root", password);
             var statement = connection.createStatement()) {
            for (String ddl : Files.readString(Path.of("../db/iot.sql")).split(";")) {
                if (!ddl.isBlank()) statement.execute(ddl);
            }
        }
        var config = new MybatisConfiguration();
        // 和正式自动配置一致地填充审计时间，不能用替身DDL的宽松空值掩盖真实约束。
        GlobalConfigUtils.setGlobalConfig(config, new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig())
                .setMetaObjectHandler(new DefaultDBFieldHandler()));
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("iot-mysql", new JdbcTransactionFactory(),
                new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", password)));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        config.addInterceptor(interceptor);
        config.addMapper(IotDeviceBindingMapper.class);
        config.addMapper(IotConnectorMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(false);
        connectors = session.getMapper(IotConnectorMapper.class);
        var connectorService = mock(IotConnectorService.class);
        when(connectorService.getConnector("mysql-pilot")).thenReturn(new IotConnectorDO());
        service = new IotDeviceBindingServiceImpl(session.getMapper(IotDeviceBindingMapper.class),
                connectorService, new IotPointMappingService(new ObjectMapper()));
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void cleanup() {
        if (session != null) { session.rollback(); session.close(); }
        TenantContextHolder.clear();
    }

    private Long create(String external, boolean enabled) {
        var request = new IotDeviceBindingSaveReqVO();
        request.setConnectorId("mysql-pilot");
        request.setExternalDeviceId(external);
        request.setMappingRevision("v1");
        request.setEnabled(enabled);
        request.setPointMappingJson("");
        return service.createBinding(request);
    }

    @Test
    void shouldExecuteBoundedPagingAndIsolateTenant() {
        Long first = create("device-a", true);
        create("device-b", false);
        TenantContextHolder.setTenantId(8L);
        create("device-foreign", true);
        assertNull(service.getBinding(first));
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class, () -> service.deleteBinding(first));
        var foreignUpdate = new IotDeviceBindingSaveReqVO();
        foreignUpdate.setId(first);
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class, () -> service.updateBinding(foreignUpdate));
        TenantContextHolder.setTenantId(7L);
        var page = new IotDeviceBindingPageReqVO();
        page.setPageSize(1);
        page.setPageNo(1);
        assertEquals(2L, service.getBindingPage(page).getTotal());
        assertEquals(1, service.getBindingPage(page).getList().size());
        page.setPageNo(2);
        assertEquals(first, service.getBindingPage(page).getList().get(0).getId());
        page.setPageNo(1);
        page.setEnabled(false);
        page.setExternalDeviceId("device-b");
        assertEquals(1L, service.getBindingPage(page).getTotal());
        page.setConnectorId("missing");
        assertEquals(0L, service.getBindingPage(page).getTotal());
    }

    @Test
    void shouldPersistClearMappingAndLogicallyDeleteInMysql() {
        Long id = create("device-map", true);
        assertEquals("{}", service.getBinding(id).getPointMappingJson());
        var request = new IotDeviceBindingSaveReqVO();
        request.setId(id);
        request.setConnectorId("mysql-pilot");
        request.setExternalDeviceId("device-map");
        request.setMappingRevision("v2");
        request.setPointMappingJson("{\"points\":{\"temp\":{\"scale\":0.1234567890123456789}}}");
        service.updateBinding(request);
        // 必须比较完整原文；仅比较前15位会掩盖MySQL JSON列将长小数转为double的问题。
        assertEquals(request.getPointMappingJson(), service.getBinding(id).getPointMappingJson());
        request.setPointMappingJson("");
        service.updateBinding(request);
        assertEquals("{}", service.getBinding(id).getPointMappingJson());
        service.deleteBinding(id);
        assertNull(service.getBinding(id));
        assertEquals(0L, service.getBindingPage(new IotDeviceBindingPageReqVO()).getTotal());
    }

    @Test
    void shouldReadRuntimeUsingMysqlDatabaseClockAndExplicitTenant() throws Exception {
        // 原始插入只构造当前隔离事务样本，实际快照走生产Mapper及租户插件。
        try (var statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO wx_iot_connector (id,tenant_id,connector_id,name,connector_type,enabled,version,status,runtime_owner,runtime_lease_until) "
                    + "VALUES (90001,7,'mysql-runtime','隔离测试','MQTT',1,2,'CONNECTED','test-owner',DATE_ADD(NOW(3),INTERVAL 30 SECOND))");
        }
        var runtime = connectors.selectRuntime(7L, 90001L);
        assertTrue(runtime.getLeaseActive());
        assertNotNull(runtime.getObservedAt());
        assertNull(connectors.selectRuntime(8L, 90001L));
    }
}
