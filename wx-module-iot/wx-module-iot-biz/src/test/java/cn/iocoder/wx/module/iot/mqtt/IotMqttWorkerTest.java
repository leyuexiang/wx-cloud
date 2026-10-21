package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 不建立外部连接，核验启停、失租约、配置变更与故障退避的资源行为。 */
class IotMqttWorkerTest {
    private final IotConnectorMapper connectors = mock(IotConnectorMapper.class);
    private final TenantFrameworkService tenants = mock(TenantFrameworkService.class);
    private final IotMqttLease lease = mock(IotMqttLease.class);
    private final IotMqttConnection.Factory factory = mock(IotMqttConnection.Factory.class);
    private final IotMqttConnection connection = mock(IotMqttConnection.class);
    private IotMqttWorker worker;

    @BeforeEach
    void prepare() {
        // 直接模拟 Mapper 时框架不会启动，显式初始化真实 Lambda 条件所需的表元数据。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "mqtt-test"), IotConnectorDO.class);
        IotMqttProperties properties = new IotMqttProperties();
        properties.setTenantId(7L);
        properties.setConnectorId("pilot");
        worker = new IotMqttWorker(properties, connectors, tenants, lease, factory, new ObjectMapper());
        // 用例手工推进工作周期，不启动后台定时线程，避免基于时序碰运气。
        ReflectionTestUtils.setField(worker, "running", true);
        when(lease.isValid()).thenReturn(true);
        when(lease.acquireOrRenew()).thenReturn(true);
        when(connectors.selectByConnectorId("pilot")).thenReturn(IotMqttSettingsTest.connector());
        when(factory.create(any(), same(lease))).thenReturn(connection);
        when(connection.isConnected()).thenReturn(true);
    }

    @AfterEach
    void release() {
        worker.stop();
        TenantContextHolder.clear();
    }

    @Test
    void shouldReuseLiveConnectionAndCloseImmediatelyWhenDisabled() throws Exception {
        TenantContextHolder.setTenantId(99L);
        worker.tick();
        worker.tick();
        verify(connection, times(1)).connect();
        verify(factory, times(1)).create(any(), same(lease));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<IotConnectorDO>> updates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(connectors, atLeastOnce()).update(isNull(), updates.capture());
        for (var update : updates.getAllValues()) {
            String conditions = update.getSqlSegment();
            assertTrue(conditions.contains("runtime_owner IS NULL"));
            assertTrue(conditions.contains("runtime_lease_until <= NOW(3)"));
            assertTrue(conditions.contains("version"));
        }
        var disabled = IotMqttSettingsTest.connector();
        disabled.setEnabled(false);
        when(connectors.selectByConnectorId("pilot")).thenReturn(disabled);
        worker.tick();
        verify(connection).close();
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void shouldCloseOldConnectionBeforeGivingUpLostLease() {
        worker.tick();
        when(lease.isValid()).thenReturn(false);
        when(lease.acquireOrRenew()).thenReturn(false);
        worker.tick();
        verify(connection).close();
        verify(factory, times(1)).create(any(), same(lease));
    }

    @Test
    void shouldNotRetryFailedConnectEveryWorkCycle() throws Exception {
        doThrow(new IllegalStateException("connect failed")).when(connection).connect();
        worker.tick();
        worker.tick();
        verify(factory, times(1)).create(any(), same(lease));
        verify(connection).close();
    }

    @Test
    void shouldBackOffAfterShortLivedConnectionWithoutResettingFailures() throws Exception {
        ReflectionTestUtils.setField(worker, "failures", 3);
        worker.tick();
        worker.tick();
        assertEquals(3, ReflectionTestUtils.getField(worker, "failures"));
        when(connection.isConnected()).thenReturn(false);
        worker.tick();
        worker.tick();
        assertEquals(4, ReflectionTestUtils.getField(worker, "failures"));
        verify(connection).close();
        verify(factory, times(1)).create(any(), same(lease));
    }

    @Test
    void shouldResetFailureCountOnlyAfterStableConnection() {
        ReflectionTestUtils.setField(worker, "failures", 3);
        worker.tick();
        // 直接推进单调时间基准，避免为验证稳定窗口实际等待一分钟。
        ReflectionTestUtils.setField(worker, "connectedSince", System.nanoTime() - TimeUnit.SECONDS.toNanos(61));
        worker.tick();
        assertEquals(0, ReflectionTestUtils.getField(worker, "failures"));
    }

    @Test
    void shouldCloseBeforeReplacingChangedConfiguration() {
        worker.tick();
        var changed = IotMqttSettingsTest.connector();
        changed.setConfigJson("{\"topics\":[\"pilot/other\"],\"qos\":1}");
        when(connectors.selectByConnectorId("pilot")).thenReturn(changed);
        worker.tick();
        var order = inOrder(connection, factory);
        order.verify(factory).create(any(), same(lease));
        order.verify(connection).close();
        order.verify(factory).create(any(), same(lease));
    }

    @Test
    void shouldReleaseOnStopAndRejectFurtherWork() {
        worker.tick();
        worker.stop();
        worker.tick();
        assertFalse(worker.isRunning());
        verify(lease).release();
        verify(connection).close();
        verify(factory, times(1)).create(any(), same(lease));
    }
}
