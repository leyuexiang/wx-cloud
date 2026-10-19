package cn.iocoder.wx.module.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 调用真实 Paho 回调逻辑，以测试替身控制数据库接收结果与租约生命周期。 */
@SuppressWarnings("unchecked")
class IotMqttAcknowledgementTest {
    private final MqttAsyncClient client = mock(MqttAsyncClient.class);
    private final IotMqttLease lease = mock(IotMqttLease.class);
    private final BiConsumer<String, byte[]> receiver = mock(BiConsumer.class);
    private final IotMqttSettings settings = IotMqttSettings.parse(IotMqttSettingsTest.connector(), false, new ObjectMapper());
    private final IotMqttSettings.Credentials credentials = new IotMqttSettings.Credentials("user", "secret".toCharArray());

    private MqttCallback callback() {
        new PahoIotMqttConnection(client, settings, credentials, lease, receiver);
        var callback = ArgumentCaptor.forClass(MqttCallback.class);
        verify(client).setCallback(callback.capture());
        verify(client).setManualAcks(true);
        return callback.getValue();
    }

    private MqttMessage message(int qos) {
        var message = new MqttMessage(new byte[]{1, 2});
        message.setQos(qos);
        message.setId(42);
        return message;
    }

    @Test
    void shouldAcknowledgeOnlyAfterDurableReceiverReturns() throws Exception {
        when(lease.isValid()).thenReturn(true);
        callback().messageArrived("pilot/telemetry", message(1));
        var order = inOrder(receiver, client);
        order.verify(receiver).accept(eq("pilot/telemetry"), any());
        order.verify(client).messageArrivedComplete(42, 1);
    }

    @Test
    void shouldNotAcknowledgeDatabaseFailure() throws Exception {
        when(lease.isValid()).thenReturn(true);
        doThrow(new IllegalStateException("database failure")).when(receiver).accept(anyString(), any());
        var callback = callback();
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(1)));
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void shouldNotAcknowledgeLeaseLostDuringDatabaseWrite() throws Exception {
        when(lease.isValid()).thenReturn(true, false);
        var callback = callback();
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(1)));
        verify(receiver).accept(anyString(), any());
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void shouldRejectExpiredLeaseWithoutIngesting() throws Exception {
        var callback = callback();
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(1)));
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void shouldRejectQosZeroEvenWithValidLease() throws Exception {
        // 订阅 QoS 1 不能提升发布方的 QoS 0，不能将其当作可重投消息接收。
        when(lease.isValid()).thenReturn(true);
        var callback = callback();
        assertThrows(IllegalStateException.class, () -> callback.messageArrived("pilot/telemetry", message(0)));
        verifyNoInteractions(receiver);
        verify(client, never()).messageArrivedComplete(anyInt(), anyInt());
    }

    @Test
    void shouldRequirePersistentSessionTlsHostnameCheckAndGrantedQosOne() throws Exception {
        IMqttToken connect = mock(IMqttToken.class);
        IMqttToken subscribed = mock(IMqttToken.class);
        when(client.connect(any(MqttConnectOptions.class))).thenReturn(connect);
        when(client.subscribe(any(String[].class), any(int[].class))).thenReturn(subscribed);
        when(subscribed.getGrantedQos()).thenReturn(new int[]{0});
        var connection = new PahoIotMqttConnection(client, settings, credentials, lease, receiver);
        assertThrows(MqttException.class, connection::connect);
        var options = ArgumentCaptor.forClass(MqttConnectOptions.class);
        verify(client).connect(options.capture());
        assertFalse(options.getValue().isCleanSession());
        assertFalse(options.getValue().isAutomaticReconnect());
        assertTrue(options.getValue().isHttpsHostnameVerificationEnabled());
        assertEquals(MqttConnectOptions.MQTT_VERSION_3_1_1, options.getValue().getMqttVersion());
        assertArrayEquals(new char[6], credentials.password());
        connection.close();
        verify(client).close(true);
    }
}
