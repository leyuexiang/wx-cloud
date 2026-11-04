package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** 以单键 Lua 原子比较保存最新状态，支持不同分区消费者并发写入。 */
public class RedisIotStateStore implements IotStateStore {
    private static final DefaultRedisScript<Long> UPSERT = new DefaultRedisScript<>();
    static {
        UPSERT.setLocation(new ClassPathResource("iot/state-upsert.lua"));
        UPSERT.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final IotTelemetryEventCodec codec;

    public RedisIotStateStore(StringRedisTemplate redis, IotTelemetryEventCodec codec) {
        this.redis = redis;
        this.codec = codec;
    }

    @Override
    public boolean putLatest(TwinTelemetryEvent event, long remainingTtlMillis) {
        if (remainingTtlMillis <= 0) {
            throw new IllegalArgumentException("状态剩余保留期必须为正数");
        }
        Long result = redis.execute(UPSERT,
                List.of(key(event.getTenantId(), event.getDeviceId(), event.getPointCode())),
                version(event), codec.encode(event), Long.toString(remainingTtlMillis));
        if (result == null) {
            throw new IllegalStateException("Redis 未返回投影写入结果");
        }
        return result == 1;
    }

    @Override
    public TwinTelemetryEvent get(Long tenantId, String deviceId, String pointCode) {
        Object payload = redis.opsForHash().get(key(tenantId, deviceId, pointCode), "payload");
        if (payload == null) {
            return null;
        }
        TwinTelemetryEvent event = codec.decode(payload.toString());
        // 缓存内容损坏时拒绝返回，避免错误键下的其他租户或设备数据被泄露。
        if (event == null || !Objects.equals(tenantId, event.getTenantId())
                || !Objects.equals(deviceId, event.getDeviceId()) || !Objects.equals(pointCode, event.getPointCode())) {
            throw new IllegalStateException("投影缓存身份与查询键不一致");
        }
        return event;
    }

    static String key(Long tenantId, String deviceId, String pointCode) {
        if (tenantId == null || tenantId < 0 || deviceId == null || deviceId.isBlank()
                || pointCode == null || pointCode.isBlank()) {
            throw new IllegalArgumentException("状态查询必须提供有效租户、设备与点位");
        }
        return "twin:iot-state:v1:" + tenantId + ":" + encodeKey(deviceId) + ":" + encodeKey(pointCode);
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String version(TwinTelemetryEvent event) {
        // 平移后的秒数覆盖 Instant 全范围；定长十进制字符串避免 Lua 双精度数丢失纳秒/大整数精度。
        String seconds = Long.toString(event.getEventTime().getEpochSecond() - Instant.MIN.getEpochSecond());
        String nanos = Integer.toString(event.getEventTime().getNano());
        // 同一事件时间以事件编号确定稳定结果；该平局规则不代表设备的实际发生先后。
        return "0".repeat(17 - seconds.length()) + seconds + "0".repeat(9 - nanos.length())
                + nanos + ":" + event.getEventId();
    }
}
