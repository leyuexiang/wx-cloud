package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 管理配置复用运行时解析器，不建立网络连接，也不要求保存时读取真实凭据。 */
class IotConnectorConfigValidatorTest {
    private IotConnectorDO connector() {
        var row = new IotConnectorDO();
        row.setConnectorType("MQTT");
        row.setEndpoint("ssl://broker.example.com:8883");
        row.setCredentialRef("IOT_MQTT_PILOT");
        row.setConfigJson("{\"topics\":[\"pilot/telemetry\"],\"qos\":1}");
        return row;
    }

    @Test
    void shouldAcceptValidConfigurationWithoutReadingCredentials() {
        assertDoesNotThrow(() -> new IotConnectorConfigValidator(new ObjectMapper(), false).validate(connector()));
    }

    @Test
    void shouldRejectPlaintextUnlessDeploymentExplicitlyPermitsIt() {
        var row = connector();
        row.setEndpoint("tcp://broker.example.com:1883");
        assertThrows(ServiceException.class, () -> new IotConnectorConfigValidator(new ObjectMapper(), false).validate(row));
        assertDoesNotThrow(() -> new IotConnectorConfigValidator(new ObjectMapper(), true).validate(row));
    }

    @Test
    void shouldRejectInvalidSubscriptionBeforePublishingConfiguration() {
        var row = connector();
        row.setConfigJson("{\"topics\":[\"pilot/#\"],\"qos\":1}");
        assertThrows(ServiceException.class, () -> new IotConnectorConfigValidator(new ObjectMapper(), false).validate(row));
    }
}
