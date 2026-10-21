package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 检查只允许明确主题、TLS 和受控凭据引用，不把凭据放进异常或客户端编号。 */
class IotMqttSettingsTest {
    static IotConnectorDO connector() {
        IotConnectorDO connector = new IotConnectorDO();
        connector.setId(1L);
        connector.setTenantId(7L);
        connector.setConnectorId("pilot");
        connector.setConnectorType("MQTT");
        connector.setEnabled(true);
        connector.setVersion(1);
        connector.setEndpoint("ssl://broker.example.com:8883");
        connector.setCredentialRef("IOT_MQTT_PILOT");
        connector.setConfigJson("{\"topics\":[\"pilot/telemetry\"],\"qos\":1}");
        return connector;
    }

    @Test
    void shouldKeepClientIdStableForRestartsAndSeparateChangedSubscriptions() {
        IotConnectorDO connector = connector();
        var settings = IotMqttSettings.parse(connector, false, new ObjectMapper());
        assertEquals(settings.clientId(), IotMqttSettings.parse(connector, false, new ObjectMapper()).clientId());
        connector.setTenantId(8L);
        assertNotEquals(settings.clientId(), IotMqttSettings.parse(connector, false, new ObjectMapper()).clientId());
        connector.setTenantId(7L);
        connector.setConfigJson("{\"topics\":[\"other/telemetry\"],\"qos\":1}");
        assertFalse(settings.matches(connector));
        assertNotEquals(settings.clientId(), IotMqttSettings.parse(connector, false, new ObjectMapper()).clientId());
    }

    @Test
    void shouldRejectPlaintextByDefaultAndCredentialsInUri() {
        var connector = connector();
        connector.setEndpoint("tcp://broker.example.com:1883");
        assertThrows(IllegalArgumentException.class, () -> IotMqttSettings.parse(connector, false, new ObjectMapper()));
        assertDoesNotThrow(() -> IotMqttSettings.parse(connector, true, new ObjectMapper()));
        connector.setEndpoint("ssl://secret:password@broker.example.com:8883");
        var error = assertThrows(IllegalArgumentException.class, () -> IotMqttSettings.parse(connector, false, new ObjectMapper()));
        assertFalse(error.toString().contains("password"));
        assertNull(error.getCause());
    }

    @Test
    void shouldRejectWildcardTopicsAndUnsupportedQosAndInlineSecrets() {
        for (String config : new String[]{"{\"topics\":[\"pilot/#\"],\"qos\":1}",
                "{\"topics\":[\"pilot/+\"],\"qos\":1}", "{\"topics\":[],\"qos\":1}",
                "{\"topics\":[\"pilot\"],\"qos\":0}", "{\"topics\":[\"pilot\"],\"qos\":1,\"password\":\"secret\"}"}) {
            var connector = connector();
            connector.setConfigJson(config);
            assertThrows(IllegalArgumentException.class, () -> IotMqttSettings.parse(connector, false, new ObjectMapper()));
        }
    }

    @Test
    void shouldReadOnlyReferencedCredentialsWithoutPrintingThem() {
        var settings = IotMqttSettings.parse(connector(), false, new ObjectMapper());
        var credentials = settings.credentials(name -> {
            assertEquals("IOT_MQTT_PILOT", name);
            return "{\"username\":\"operator\",\"password\":\"do-not-print\"}";
        }, new ObjectMapper());
        assertArrayEquals("do-not-print".toCharArray(), credentials.password());
        assertFalse(credentials.toString().contains("do-not-print"));
        assertThrows(IllegalArgumentException.class, () -> settings.credentials(name -> null, new ObjectMapper()));
    }
}
