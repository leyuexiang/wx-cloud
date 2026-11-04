package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.api.IotConnectorType;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO.Outcome;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.mqtt.IotMqttConnectivityTester;
import cn.iocoder.wx.module.iot.mqtt.IotMqttSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_CONFIG_INVALID;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS;

/** 连接测试与正式运行租约隔离，只读取当前租户保存的 MQTT 配置并使用临时会话探测。 */
@Service
@Validated
@RequiredArgsConstructor
public class IotConnectorConnectivityService {
    private final IotConnectorMapper connectors;
    private final IotConnectorConfigValidator configValidator;
    private final IotMqttConnectivityTester tester;

    public IotConnectorConnectionTestRespVO test(IotConnectorConnectionTestReqVO request) {
        // 当前线程已由管理接口建立租户上下文，MyBatis 租户拦截器负责追加租户条件。
        IotConnectorDO connector = connectors.selectById(request.getId());
        if (connector == null) {
            throw exception(CONNECTOR_NOT_EXISTS);
        }
        if (!IotConnectorType.MQTT.name().equals(connector.getConnectorType())) {
            return new IotConnectorConnectionTestRespVO(connector.getConnectorId(), Outcome.UNSUPPORTED_PROTOCOL,
                    0, 0, "当前仅支持 MQTT 连接测试");
        }
        IotMqttSettings settings;
        try {
            settings = configValidator.parseMqtt(connector);
        } catch (RuntimeException ex) {
            throw exception(CONNECTOR_CONFIG_INVALID);
        }
        int timeout = request.getTimeoutSeconds() == null ? 10 : request.getTimeoutSeconds();
        return tester.test(settings, Duration.ofSeconds(timeout));
    }
}
