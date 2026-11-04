package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static cn.iocoder.wx.module.dimensions.iot.IotTelemetryEventCodec.InvalidEventException;

/** 将标准遥测投影到当前态；过期重放不恢复已淘汰的状态。 */
public class IotStateProjectionService {
    private final IotStateStore store;
    private final IotTelemetryEventCodec codec;
    private final IotStateProjectionProperties properties;
    private final Clock clock;

    public IotStateProjectionService(IotStateStore store, IotTelemetryEventCodec codec,
                                     IotStateProjectionProperties properties, Clock clock) {
        this.store = store;
        this.codec = codec;
        this.properties = properties;
        this.clock = clock;
    }

    public boolean project(String payload) {
        TwinTelemetryEvent event = codec.decode(payload);
        if (event == null || !"1.0".equals(event.getSchemaVersion()) || event.getTenantId() == null
                || event.getTenantId() < 0 || !validId(event.getEventId()) || !validId(event.getDeviceId())
                || !validId(event.getPointCode()) || event.getEventTime() == null || event.getValueType() == null) {
            throw new InvalidEventException("遥测版本或必要身份字段无效");
        }
        Instant now = clock.instant();
        // 限制设备时钟超前，避免一条远期事件长期阻挡正常遥测更新。
        if (event.getEventTime().isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new InvalidEventException("遥测时间超出允许的五分钟时钟偏差");
        }
        Instant cutoff = now.minus(properties.getStateTtl());
        if (!event.getEventTime().isAfter(cutoff)) {
            return false;
        }
        long remainingTtl = Duration.between(cutoff, event.getEventTime()).toMillis();
        return remainingTtl > 0 && store.putLatest(event, remainingTtl);
    }

    private boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= 256;
    }
}
