package cn.iocoder.wx.module.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 控制异步任务的实际执行时点，证明排队、失败及关闭不能产生提前确认。 */
@SuppressWarnings("unchecked")
class IotMqttAsyncAcknowledgementTest {
    private final MqttAsyncClient client = mock(MqttAsyncClient.class);
    private final IotMqttLease lease = mock(IotMqttLease.class);
    private final BiConsumer<String, byte[]> receiver = mock(BiConsumer.class);
    private final List<Runnable> pending = new CopyOnWriteArrayList<>();
    private PahoIotMqttConnection connection;

    private MqttCallback callback(Executor executor, int inFlight) {
        when(lease.isValid()).thenReturn(true);
        when(lease.generation()).thenReturn(1L);
        var settings = IotMqttSettings.parse(IotMqttSettingsTest.connector(), false, new ObjectMapper());
        connection = new PahoIotMqttConnection(client, settings,
                new IotMqttSettings.Credentials("user", "temporary".toCharArray()), lease, receiver, executor, inFlight);
        var captured = ArgumentCaptor.forClass(MqttCallback.class);
        verify(client).setCallback(captured.capture());
        return captured.getValue();
    }

    private MqttMessage message(int id) {
        var message = new MqttMessage(new byte[]{1, 2});
        message.setQos(1);
        message.setId(id);
        return message;
    }

    @Test
    void queuedMessageMustNotBeAcknowledgedBeforeDurableReception() throws Exception {
        var callback = callback(pending::add, 2);
        callback.messageArrived("pilot/telemetry", message(42));
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
        pending.remove(0).run();
        var order = inOrder(receiver, client);
        order.verify(receiver).accept(eq("pilot/telemetry"), any());
        order.verify(client).messageArrivedComplete(42, 1);
    }

    @Test
    void queuedPayloadMustHaveIndependentOwnership() throws Exception {
        var callback = callback(pending::add, 2);
        var input = message(42);
        callback.messageArrived("pilot/telemetry", input);
        input.getPayload()[0] = 9;
        pending.remove(0).run();
        var body = ArgumentCaptor.forClass(byte[].class);
        verify(receiver).accept(anyString(), body.capture());
        assertArrayEquals(new byte[]{1, 2}, body.getValue());
    }

    @Test
    void databaseFailureMustInvalidateSessionWithoutAcknowledging() throws Exception {
        var callback = callback(pending::add, 2);
        when(client.isConnected()).thenReturn(true);
        doThrow(new IllegalStateException("受控落库失败")).when(receiver).accept(anyString(), any());
        callback.messageArrived("pilot/telemetry", message(42));
        pending.remove(0).run();
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
        assertFalse(connection.isConnected());
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(43)));
    }

    @Test
    void leaseLostWhileQueuedMustNotIngestOrAcknowledge() throws Exception {
        var callback = callback(pending::add, 2);
        callback.messageArrived("pilot/telemetry", message(42));
        when(lease.isValid()).thenReturn(false);
        pending.remove(0).run();
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void oldGenerationMustNotAcknowledgeEvenAfterLeaseReturns() throws Exception {
        var callback = callback(pending::add, 2);
        callback.messageArrived("pilot/telemetry", message(42));
        when(lease.generation()).thenReturn(2L);
        pending.remove(0).run();
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void closeMustPreventQueuedAndLateDurableCallbacksFromAcknowledging() throws Exception {
        var callback = callback(pending::add, 2);
        callback.messageArrived("pilot/telemetry", message(42));
        doAnswer(invocation -> { connection.close(); return null; }).when(receiver).accept(anyString(), any());
        pending.remove(0).run();
        verify(receiver).accept(anyString(), any());
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(43)));
    }

    @Test
    void rejectionMustLeaveMessageUnacknowledgedAndCloseAdmission() throws Exception {
        var callback = callback(task -> { throw new RejectedExecutionException(); }, 2);
        assertThrows(RejectedExecutionException.class, () -> callback.messageArrived("pilot/telemetry", message(42)));
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
        assertFalse(connection.isConnected());
    }

    @Test
    void closedConnectionMustSkipTasksThatHaveNotStarted() throws Exception {
        var callback = callback(pending::add, 2);
        callback.messageArrived("pilot/telemetry", message(42));
        connection.close();
        connection.close();
        pending.remove(0).run();
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
        verify(client, times(1)).close(true);
    }

    @Test
    void acknowledgementFailureMustInvalidateTheConnection() throws Exception {
        var callback = callback(pending::add, 2);
        doThrow(new MqttException(32100)).when(client).messageArrivedComplete(anyInt(), anyInt());
        callback.messageArrived("pilot/telemetry", message(42));
        pending.remove(0).run();
        verify(receiver).accept(anyString(), any());
        assertFalse(connection.isConnected());
    }

    @Test
    void inFlightLimitMustBackpressureUntilAnEarlierTaskFinishes() throws Exception {
        var callback = callback(pending::add, 1);
        callback.messageArrived("pilot/telemetry", message(42));
        var caller = Executors.newSingleThreadExecutor();
        try {
            var next = caller.submit(() -> { callback.messageArrived("pilot/telemetry", message(43)); return true; });
            assertThrows(TimeoutException.class, () -> next.get(100, TimeUnit.MILLISECONDS));
            assertEquals(1, pending.size());
            pending.remove(0).run();
            assertTrue(next.get(2, TimeUnit.SECONDS));
            assertEquals(1, pending.size());
            pending.remove(0).run();
            verify(client).messageArrivedComplete(42, 1);
            verify(client).messageArrivedComplete(43, 1);
        } finally { caller.shutdownNow(); }
    }

    @Test
    void sharedPoolMustBeBoundedAndCancelQueuedTasksOnShutdown() throws Exception {
        var entered = new CountDownLatch(1);
        var wait = new CountDownLatch(1);
        try (var dispatcher = new IotMqttReceiveDispatcher(1, 1)) {
            dispatcher.execute(() -> {
                entered.countDown();
                try { wait.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var queued = new FutureTask<Void>(() -> { fail("关闭时不得执行已排队的接收"); return null; });
            dispatcher.execute(queued);
            assertThrows(RejectedExecutionException.class, () -> dispatcher.execute(() -> {}));
            dispatcher.close();
            assertTrue(queued.isCancelled());
            assertThrows(RejectedExecutionException.class, () -> dispatcher.execute(() -> {}));
        } finally { wait.countDown(); }
    }

    @Test
    void shutdownMustDrainQueuedPublicationResultsWhileCancellingReceptions() throws Exception {
        var entered = new CountDownLatch(1);
        var written = new java.util.concurrent.atomic.AtomicInteger();
        try (var dispatcher = new IotMqttReceiveDispatcher(1, 1)) {
            dispatcher.execute(() -> {
                entered.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            dispatcher.execute(written::incrementAndGet);
            dispatcher.close();
            assertEquals(1, written.get());
        }
    }
}
