package cn.iocoder.wx.module.iot.mqtt;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 一个工作进程管理一个明确租户和连接器；关闭时无需提供外部凭据。 */
@Data
@Validated
@ConfigurationProperties("wx.iot.mqtt")
public class IotMqttProperties {
    private boolean enabled;
    private Long tenantId;
    private String connectorId;
    private boolean allowInsecure;

    @AssertTrue(message = "启用 MQTT 时必须提供非负租户号及 1 至 64 字符的连接器编号")
    public boolean isIdentityValid() {
        return !enabled || tenantId != null && tenantId >= 0 && connectorId != null
                && !connectorId.isBlank() && connectorId.length() <= 64;
    }
}
