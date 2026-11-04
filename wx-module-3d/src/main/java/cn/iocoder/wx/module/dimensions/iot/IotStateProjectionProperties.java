package cn.iocoder.wx.module.dimensions.iot;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 投影使用独立开关和消费组，状态缓存不承担历史审计职责。 */
@Data
@Validated
@ConfigurationProperties("wx.iot-state")
public class IotStateProjectionProperties {
    private boolean enabled;
    @NotBlank
    private String consumerGroup = "dimensions-iot-state-v1";
    @NotBlank
    private String deadLetterTopic = "twin.event.v1.dimensions.dlt";
    @NotNull
    private Duration stateTtl = Duration.ofDays(1);

    /** 限制缓存保留范围，防止误配永久缓存或毫秒级过期。 */
    @AssertTrue(message = "状态保留期必须在 1 秒至 365 天之间")
    public boolean isStateTtlValid() {
        return stateTtl != null && stateTtl.compareTo(Duration.ofSeconds(1)) >= 0
                && stateTtl.compareTo(Duration.ofDays(365)) <= 0;
    }

    /** 死信不能回到源主题，否则毒消息会形成无限发布循环。 */
    @AssertTrue(message = "投影死信主题不能与遥测源主题相同")
    public boolean isDeadLetterTopicValid() {
        return !cn.iocoder.wx.module.iot.api.IotTopicsConstants.TELEMETRY_EVENT.equals(deadLetterTopic);
    }
}
