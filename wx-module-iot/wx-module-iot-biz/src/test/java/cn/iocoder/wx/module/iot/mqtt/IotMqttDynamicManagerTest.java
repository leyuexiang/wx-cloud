package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用受控执行器推进发现、连接、关闭及租约交接，不访问外部 Broker。 */
class IotMqttDynamicManagerTest {
    private final IotConnectorMapper connectors = mock(IotConnectorMapper.class);
    private final TenantFrameworkService tenants = mock(TenantFrameworkService.class);
    private final IotMqttConnection.Factory factory = mock(IotMqttConnection.Factory.class);
    private final IotMqttManualExecutor controls = new IotMqttManualExecutor();
    private final IotMqttManualExecutor connections = new IotMqttManualExecutor();
    private final IotMqttManualExecutor cleanups = new IotMqttManualExecutor();
    private final Map<Long, IotConnectorDO> rows = new LinkedHashMap<>();
    private final Map<Long, IotMqttConnection> clients = new LinkedHashMap<>();
    private final Map<Long, IotMqttLease> leases = new LinkedHashMap<>();
    private IotMqttDynamicManager manager;

    @BeforeEach
    void prepare() {
        IotMqttProperties properties = new IotMqttProperties();
        properties.setEnabled(true);
        properties.setMode(IotMqttProperties.Mode.DYNAMIC);
        properties.setTenantId(7L);
        properties.setMaxConnections(2);
        properties.setConnectConcurrency(2);
        properties.setDiscoveryBatchSize(1);
        add(1L);
        add(2L);
        when(connectors.selectEnabledMqttBatch(eq(7L), anyLong(), anyInt())).thenAnswer(invocation -> {
            assertEquals(7L, TenantContextHolder.getTenantId());
            long after = invocation.getArgument(1);
            int limit = invocation.getArgument(2);
            return rows.values().stream().filter(row -> row.getId() > after && Boolean.TRUE.equals(row.getEnabled()))
                    .limit(limit).toList();
        });
        when(connectors.selectById(anyLong())).thenAnswer(invocation -> rows.get(invocation.getArgument(0)));
        when(connectors.claimMqttRuntime(eq(7L), anyLong(), anyInt(), anyString())).thenReturn(1);
        when(connectors.renewMqttRuntime(eq(7L), anyLong(), anyInt(), anyString())).thenReturn(1);
        when(connectors.updateMqttRuntimeStatus(eq(7L), anyLong(), anyInt(), anyString(), anyString())).thenReturn(1);
        when(factory.create(any(), any())).thenAnswer(invocation -> clients.get(((IotMqttSettings) invocation.getArgument(0)).id()));
        manager = new IotMqttDynamicManager(properties, connectors, tenants, new ObjectMapper(),
                row -> leases.get(row.getId()), factory, mock(ScheduledExecutorService.class), controls, connections, cleanups);
        manager.start();
    }

    private void add(Long id) {
        IotConnectorDO row = IotMqttSettingsTest.connector();
        row.setId(id);
        row.setConnectorId("connector-" + id);
        rows.put(id, row);
        IotMqttLease lease = mock(IotMqttLease.class);
        when(lease.acquireOrRenew()).thenReturn(true);
        when(lease.isValid()).thenReturn(true);
        when(lease.ownerToken()).thenReturn("owner-" + id);
        leases.put(id, lease);
        IotMqttConnection client = mock(IotMqttConnection.class);
        when(client.isConnected()).thenReturn(true);
        clients.put(id, client);
    }

    private void control() {
        manager.pollSessions();
        controls.drain();
    }

    private void connected() {
        manager.discover();
        control();
        connections.drain();
        control();
    }

    @AfterEach
    void close() {
        manager.stop();
        controls.drain();
        connections.drain();
        cleanups.drain();
        TenantContextHolder.clear();
    }

    @Test
    void shouldRunTwoConnectionsAndRestoreCallerTenant() throws Exception {
        TenantContextHolder.setTenantId(99L);
        connected();
        verify(clients.get(1L)).connect();
        verify(clients.get(2L)).connect();
        verify(connectors).updateMqttRuntimeStatus(7L, 1L, 1, "owner-1", "CONNECTED");
        verify(connectors).updateMqttRuntimeStatus(7L, 2L, 1, "owner-2", "CONNECTED");
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void shouldDiscoverNewConnectorWithoutRestart() throws Exception {
        rows.remove(2L);
        connected();
        add(2L);
        manager.discover();
        control();
        connections.drain();
        control();
        verify(clients.get(2L)).connect();
        verify(clients.get(1L), times(1)).connect();
    }

    @Test
    void shouldCloseDisabledConnectorWithoutTouchingOtherConnection() throws Exception {
        connected();
        rows.get(1L).setEnabled(false);
        manager.discover();
        verify(leases.get(1L)).retire();
        controls.drain();
        cleanups.drain();
        verify(clients.get(1L)).close();
        verify(clients.get(2L), never()).close();
        verify(connectors).releaseMqttRuntime(7L, 1L, "owner-1");
    }

    @Test
    void shouldFinishOldCloseBeforeStartingNewVersion() throws Exception {
        connected();
        rows.get(1L).setVersion(2);
        manager.discover();
        controls.drain();
        manager.discover();
        verify(factory, times(2)).create(any(), any());
        cleanups.drain();
        manager.discover();
        control();
        connections.drain();
        control();
        var order = inOrder(clients.get(1L));
        order.verify(clients.get(1L)).connect();
        order.verify(clients.get(1L)).close();
        order.verify(clients.get(1L)).connect();
    }

    @Test
    void shouldBackOffFailedConnectorWhileOtherRemainsLive() throws Exception {
        doThrow(new IllegalStateException("unavailable")).when(clients.get(1L)).connect();
        connected();
        control();
        connections.drain();
        verify(clients.get(1L), times(1)).connect();
        verify(clients.get(2L), times(1)).connect();
        verify(connectors).updateMqttRuntimeStatus(7L, 2L, 1, "owner-2", "CONNECTED");
    }

    @Test
    void shouldRetireWhenDatabaseOwnerIsNoLongerCurrent() {
        connected();
        when(connectors.renewMqttRuntime(7L, 1L, 1, "owner-1")).thenReturn(0);
        control();
        cleanups.drain();
        verify(leases.get(1L)).retire();
        verify(clients.get(1L)).close();
        verify(clients.get(2L), never()).close();
    }

    @Test
    void shouldBoundAdmissionAndDiscoveryQueries() throws Exception {
        add(3L);
        connected();
        verify(clients.get(3L), never()).connect();
        verify(connectors).selectEnabledMqttBatch(7L, 0, 1);
        verify(connectors).selectEnabledMqttBatch(7L, 1, 1);
        verify(connectors).selectEnabledMqttBatch(7L, 2, 1);
    }

    @Test
    void shouldWaitForPendingConnectCleanupBeforeShutdownCallback() {
        manager.discover();
        control();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean stoppedAgain = new AtomicBoolean();
        manager.stop(() -> stopped.set(true));
        manager.stop(() -> stoppedAgain.set(true));
        controls.drain();
        assertFalse(stopped.get());
        assertFalse(stoppedAgain.get());
        connections.drain();
        cleanups.drain();
        assertTrue(stopped.get());
        assertTrue(stoppedAgain.get());
        verify(factory, never()).create(any(), any());
        verify(leases.get(1L)).release();
        verify(leases.get(2L)).release();
    }

    @Test
    void shouldNotConnectWhenAnotherDatabaseOwnerIsStillActive() throws Exception {
        when(connectors.claimMqttRuntime(7L, 1L, 1, "owner-1")).thenReturn(0);
        connected();
        verify(clients.get(1L), never()).connect();
        verify(clients.get(2L)).connect();
    }

    @Test
    void shouldRetireLostLeaseInsteadOfRevivingSameSession() {
        connected();
        when(leases.get(1L).isValid()).thenReturn(false);
        control();
        cleanups.drain();
        verify(leases.get(1L)).retire();
        verify(clients.get(1L)).close();
        verify(clients.get(2L), never()).close();
    }
}
