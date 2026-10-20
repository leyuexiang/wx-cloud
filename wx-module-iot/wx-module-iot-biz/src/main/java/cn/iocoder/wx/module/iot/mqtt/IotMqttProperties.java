package cn.iocoder.wx.module.iot.mqtt;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 单连接兼容模式与同租户动态模式互斥，连接及线程数均有明确上限。 */
@Data
@Validated
@ConfigurationProperties("wx.iot.mqtt")
public class IotMqttProperties {
    private boolean enabled;
    private Long tenantId;
    private String connectorId;
    private boolean allowInsecure;
    @NotNull
    private Mode mode = Mode.SINGLE;
    @Min(1) @Max(1024)
    private int maxConnections = 64;
    @Min(1) @Max(32)
    private int connectConcurrency = 4;
    @Min(1) @Max(32)
    private int controlConcurrency = 4;
    @Min(1) @Max(256)
    private int discoveryBatchSize = 32;

    public enum Mode { SINGLE, DYNAMIC }

    @AssertTrue(message = "MQTT 必须指定有效租户；SINGLE 指定连接器，DYNAMIC 留空连接器，建连并发不得超过连接上限")
    public boolean isIdentityValid() {
        return !enabled || tenantId != null && tenantId >= 0 && mode != null
                && connectConcurrency <= maxConnections
                && (mode == Mode.DYNAMIC ? connectorId == null || connectorId.isBlank()
                    : connectorId != null && !connectorId.isBlank() && connectorId.length() <= 64);
    }
}
