package cn.iocoder.wx.module.iot.mqtt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 令牌续租只允许当前所有者操作；本地保守截止时间使暂停后的旧回调失效。 */
public class IotMqttLease {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], ARGV[2]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;
    private final String key;
    private final LongSupplier nanoTime;
    private volatile long validUntil;
    private String token;

    public IotMqttLease(StringRedisTemplate redis, Long tenantId, String connectorId) {
        this(redis, tenantId, connectorId, System::nanoTime);
    }

    IotMqttLease(StringRedisTemplate redis, Long tenantId, String connectorId, LongSupplier nanoTime) {
        this.redis = redis;
        this.nanoTime = nanoTime;
        key = "iot:mqtt:lease:" + tenantId + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(connectorId.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized boolean acquireOrRenew() {
        long started = nanoTime.getAsLong();
        try {
            boolean acquired;
            if (token == null) {
                String next = UUID.randomUUID().toString();
                acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, next, TTL));
                if (acquired) {
                    token = next;
                }
            } else {
                acquired = Long.valueOf(1).equals(redis.execute(RENEW, List.of(key), token, Long.toString(TTL.toMillis())));
                if (!acquired) {
                    token = null;
                }
            }
            // 以请求开始时间计算，并预留五秒余量，不把网络延迟计入可安全接收的窗口。
            validUntil = acquired ? started + TimeUnit.SECONDS.toNanos(55) : 0;
            return acquired && isValid();
        } catch (RuntimeException ex) {
            validUntil = 0;
            throw ex;
        }
    }

    public boolean isValid() {
        long deadline = validUntil;
        return deadline != 0 && deadline - nanoTime.getAsLong() > 0;
    }

    public synchronized void release() {
        validUntil = 0;
        String previous = token;
        token = null;
        if (previous != null) {
            redis.execute(RELEASE, List.of(key), previous);
        }
    }
}
