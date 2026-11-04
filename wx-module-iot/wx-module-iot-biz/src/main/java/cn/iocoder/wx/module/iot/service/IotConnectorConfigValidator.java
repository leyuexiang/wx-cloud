package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.mqtt.IotMqttSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_CONFIG_INVALID;

/** 管理端保存和运行时共用协议参数解析规则；保存不会建立连接或读取真实密码。 */
@Component
public class IotConnectorConfigValidator {
    private final ObjectMapper mapper;
    private final boolean allowInsecure;

    public IotConnectorConfigValidator(ObjectMapper mapper,
            @Value("${wx.iot.mqtt.allow-insecure:false}") boolean allowInsecure) {
        this.mapper = mapper;
        this.allowInsecure = allowInsecure;
    }

    public void validate(IotConnectorDO connector) {
        if ("MQTT".equals(connector.getConnectorType())) {
            parseMqtt(connector);
        }
    }

    /** 预校验与保存、启用共用同一解析入口，不读取环境变量或尝试建连。 */
    public IotMqttSettings parseMqtt(IotConnectorDO connector) {
        try {
            return IotMqttSettings.parse(connector, allowInsecure, mapper);
        } catch (IllegalArgumentException ex) {
            throw exception(CONNECTOR_CONFIG_INVALID);
        }
    }
}
