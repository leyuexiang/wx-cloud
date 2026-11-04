package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真正阻塞一个网络建连任务，证明它不会阻塞其他连接的控制和续租。 */
class IotMqttDynamicSessionTest {
    @Test
    void shouldContinueRenewingAndConnectSecondSourceWhileFirstConnectIsBlocked() throws Exception {
        IotConnectorMapper mapper = mock(IotConnectorMapper.class);
        TenantFrameworkService tenants = mock(TenantFrameworkService.class);
        IotMqttConnection.Factory factory = mock(IotMqttConnection.Factory.class);
        IotMqttLease firstLease = lease("first-owner");
        IotMqttLease secondLease = lease("second-owner");
        IotConnectorDO first = IotMqttSettingsTest.connector();
        IotConnectorDO second = IotMqttSettingsTest.connector();
        second.setId(2L);
        second.setConnectorId("second");
        when(mapper.selectById(1L)).thenReturn(first);
        when(mapper.selectById(2L)).thenReturn(second);
        when(mapper.claimMqttRuntime(eq(7L), anyLong(), eq(1), anyString())).thenReturn(1);
        when(mapper.renewMqttRuntime(eq(7L), anyLong(), eq(1), anyString())).thenReturn(1);
        when(mapper.updateMqttRuntimeStatus(eq(7L), anyLong(), eq(1), anyString(), anyString())).thenReturn(1);
        IotMqttConnection blocked = mock(IotMqttConnection.class);
        IotMqttConnection fast = mock(IotMqttConnection.class);
        when(blocked.isConnected()).thenReturn(true);
        when(fast.isConnected()).thenReturn(true);
        when(factory.create(any(), any())).thenAnswer(invocation ->
                ((IotMqttSettings) invocation.getArgument(0)).id().equals(1L) ? blocked : fast);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch fastStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(blocked).connect();
        doAnswer(invocation -> { fastStarted.countDown(); return null; }).when(fast).connect();
        ExecutorService network = Executors.newFixedThreadPool(2);
        IotMqttDynamicSession a = new IotMqttDynamicSession(IotMqttSettings.parse(first, false, new ObjectMapper()),
                mapper, tenants, firstLease, factory, network, Runnable::run);
        IotMqttDynamicSession b = new IotMqttDynamicSession(IotMqttSettings.parse(second, false, new ObjectMapper()),
                mapper, tenants, secondLease, factory, network, Runnable::run);
        try {
            a.tick();
            assertTrue(started.await(3, TimeUnit.SECONDS));
            b.tick();
            assertTrue(fastStarted.await(3, TimeUnit.SECONDS));
            a.tick();
            verify(mapper).renewMqttRuntime(7L, 1L, 1, "first-owner");
            verify(firstLease, times(2)).acquireOrRenew();
        } finally {
            a.retire();
            b.retire();
            a.tick();
            b.tick();
            release.countDown();
            a.closed.get(3, TimeUnit.SECONDS);
            b.closed.get(3, TimeUnit.SECONDS);
            network.shutdown();
            assertTrue(network.awaitTermination(3, TimeUnit.SECONDS));
        }
        verify(blocked).close();
        verify(fast).close();
    }

    private IotMqttLease lease(String token) {
        IotMqttLease lease = mock(IotMqttLease.class);
        when(lease.isValid()).thenReturn(true);
        when(lease.acquireOrRenew()).thenReturn(true);
        when(lease.ownerToken()).thenReturn(token);
        return lease;
    }
}
