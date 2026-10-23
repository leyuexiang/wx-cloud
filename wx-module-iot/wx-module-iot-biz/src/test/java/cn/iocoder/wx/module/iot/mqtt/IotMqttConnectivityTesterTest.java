package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 探测使用临时清洁会话，不取得正式租约、不入库、不发布遥测。 */
class IotMqttConnectivityTesterTest {
    private IotMqttSettings settings() {
        var row = new cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO();
        row.setTenantId(7L);
        row.setId(8L);
        row.setConnectorId("pilot");
        row.setVersion(2);
        row.setEndpoint("ssl://broker.example.com:8883");
        row.setCredentialRef("IOT_MQTT_PILOT");
        row.setConfigJson("{\"topics\":[\"pilot/a\",\"pilot/b\"],\"qos\":1}");
        return IotMqttSettings.parse(row, false, new ObjectMapper());
    }

    private static IMqttToken completedToken(int... qos) throws MqttException {
        IMqttToken token = mock(IMqttToken.class);
        when(token.getGrantedQos()).thenReturn(qos);
        return token;
    }

    @Test
    void shouldConnectSubscribeAndCloseWithoutLeaseOrReceiver() throws Exception {
        var client = mock(MqttAsyncClient.class);
        var connectedToken = completedToken();
        var subscribedToken = completedToken(1, 1);
        when(client.connect(any())).thenReturn(connectedToken);
        when(client.subscribe(any(String[].class), any(int[].class))).thenReturn(subscribedToken);
        var credentials = new IotMqttSettings.Credentials("user", "secret".toCharArray());
        var tester = new IotMqttConnectivityTester(settings -> credentials,
                (endpoint, clientId) -> {
                    assertEquals("ssl://broker.example.com:8883", endpoint);
                    assertTrue(clientId.startsWith("wx-iot-probe-"));
                    return client;
                });

        var result = tester.test(settings(), Duration.ofSeconds(3));

        assertEquals(Outcome.SUCCEEDED, result.outcome());
        assertEquals(2, result.topicCount());
        verify(client).connect(argThat(options -> options.isCleanSession()
                && !options.isAutomaticReconnect() && options.getMqttVersion() == 4));
        verify(client).subscribe(eq(new String[]{"pilot/a", "pilot/b"}), eq(new int[]{1, 1}));
        verify(client).disconnectForcibly(1000, 1000, false);
        verify(client).close(true);
        assertArrayEquals(new char[6], credentials.password());
    }

    @Test
    void shouldClassifyMissingCredentialsBeforeCreatingClient() {
        var tester = new IotMqttConnectivityTester(settings -> {
            throw new IllegalArgumentException("missing");
        }, (endpoint, clientId) -> fail("凭据缺失时不能创建客户端"));

        var result = tester.test(settings(), Duration.ofSeconds(3));

        assertEquals(Outcome.CREDENTIALS_UNAVAILABLE, result.outcome());
        assertEquals(0, result.topicCount());
    }

    @Test
    void shouldClassifyBrokerFailureAndStillCloseClient() throws Exception {
        var client = mock(MqttAsyncClient.class);
        when(client.connect(any())).thenThrow(new MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR));
        var credentials = new IotMqttSettings.Credentials("user", "secret".toCharArray());
        var tester = new IotMqttConnectivityTester(settings -> credentials, (endpoint, clientId) -> client);

        var result = tester.test(settings(), Duration.ofSeconds(3));

        assertEquals(Outcome.CONNECT_FAILED, result.outcome());
        verify(client).disconnectForcibly(1000, 1000, false);
        verify(client).close(true);
        assertArrayEquals(new char[6], credentials.password());
    }

    @Test
    void shouldRejectSubscriptionDowngradeAndNeverLeaveClientOpen() throws Exception {
        var client = mock(MqttAsyncClient.class);
        var connectedToken = completedToken();
        var subscribedToken = completedToken(0, 1);
        when(client.connect(any())).thenReturn(connectedToken);
        when(client.subscribe(any(String[].class), any(int[].class))).thenReturn(subscribedToken);
        var credentials = new IotMqttSettings.Credentials("user", "secret".toCharArray());
        var tester = new IotMqttConnectivityTester(settings -> credentials, (endpoint, clientId) -> client);

        var result = tester.test(settings(), Duration.ofSeconds(3));

        assertEquals(Outcome.SUBSCRIBE_FAILED, result.outcome());
        verify(client).disconnectForcibly(1000, 1000, false);
        verify(client).close(true);
    }
}
