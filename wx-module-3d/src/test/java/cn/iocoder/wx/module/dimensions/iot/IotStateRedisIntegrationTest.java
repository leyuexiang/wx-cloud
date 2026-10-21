package cn.iocoder.wx.module.dimensions.iot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接显式指定的本机测试端口，清理本用例随机租户键，不清空共享数据库。 */
@EnabledIfEnvironmentVariable(named = "IOT_STATE_TEST_REDIS_PORT", matches = "[0-9]+")
class IotStateRedisIntegrationTest {
    private RedissonClient client;
    private StringRedisTemplate redis;
    private RedisIotStateStore store;
    private long tenantId;
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void connect() {
        // 使用工程已有的 Redisson 连接工厂，验证生产驱动实际执行 Lua 的路径。
        Config config = new Config().setThreads(2).setNettyThreads(2);
        config.useSingleServer().setAddress("redis://127.0.0.1:" + System.getenv("IOT_STATE_TEST_REDIS_PORT"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(4)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(1);
        client = Redisson.create(config);
        redis = new StringRedisTemplate(new RedissonConnectionFactory(client));
        store = new RedisIotStateStore(redis, new IotTelemetryEventCodec());
        tenantId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        keys.add(RedisIotStateStore.key(tenantId, "device-1", "temperature"));
    }

    @AfterEach
    void disconnect() {
        try {
            if (redis != null) {
                redis.delete(keys);
            }
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private cn.iocoder.wx.module.iot.api.TwinTelemetryEvent event(Instant time, String id) {
        var event = IotStateProjectionTest.event(time, id);
        event.setTenantId(tenantId);
        return event;
    }

    @Test
    void shouldIgnoreDuplicatesAndOldEventsWithoutExtendingTtl() {
        Instant now = Instant.now();
        var current = event(now, "event-b");
        assertTrue(store.putLatest(current, 60000));
        redis.expire(keys.get(0), Duration.ofSeconds(3));
        assertFalse(store.putLatest(current, 60000));
        assertFalse(store.putLatest(event(now.minusNanos(1), "event-z"), 60000));
        assertFalse(store.putLatest(event(now, "event-a"), 60000));
        assertTrue(redis.getExpire(keys.get(0), TimeUnit.MILLISECONDS) <= 3000);
        assertEquals(current, store.get(tenantId, "device-1", "temperature"));
        assertTrue(store.putLatest(event(now.plusNanos(1), "event-a"), 60000));
        assertTrue(redis.getExpire(keys.get(0), TimeUnit.MILLISECONDS) > 3000);
        assertNull(store.get(tenantId + 1, "device-1", "temperature"));
    }

    @Test
    void shouldKeepNewestEventUnderConcurrentWrites() throws Exception {
        Instant now = Instant.now();
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 19; i >= 0; i--) {
                var event = event(now.plusNanos(i), "event-" + i);
                tasks.add(() -> store.putLatest(event, 60000));
            }
            for (var result : executor.invokeAll(tasks)) {
                result.get();
            }
            assertEquals(now.plusNanos(19), store.get(tenantId, "device-1", "temperature").getEventTime());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectCorruptedTenantIdentity() {
        var event = event(Instant.now(), "event-b");
        event.setTenantId(tenantId + 1);
        redis.opsForHash().put(keys.get(0), "payload", new IotTelemetryEventCodec().encode(event));
        assertThrows(IllegalStateException.class, () -> store.get(tenantId, "device-1", "temperature"));
    }
}
