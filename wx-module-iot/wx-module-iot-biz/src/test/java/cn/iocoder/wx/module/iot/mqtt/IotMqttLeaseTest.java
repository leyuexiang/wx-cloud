package cn.iocoder.wx.module.iot.mqtt;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 本地时限使用单调时钟；Redis 响应失败或比较失败时旧回调立即失效。 */
@SuppressWarnings("unchecked")
class IotMqttLeaseTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AtomicLong time = new AtomicLong(100);
    private final IotMqttLease lease = new IotMqttLease(redis, 7L, "pilot", time::get);

    private void acquire() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60)))).thenReturn(true);
        assertTrue(lease.acquireOrRenew());
    }

    @Test
    void shouldExpireLocallyBeforeRedisDeadline() {
        acquire();
        assertTrue(lease.isValid());
        time.addAndGet(Duration.ofSeconds(55).toNanos());
        assertFalse(lease.isValid());
    }

    @Test
    void shouldInvalidateWhenRenewalTokenNoLongerMatches() {
        acquire();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertFalse(lease.acquireOrRenew());
        assertFalse(lease.isValid());
    }

    @Test
    void shouldInvalidateOnRedisFailureAndReleaseOnlyItsToken() {
        acquire();
        doThrow(new IllegalStateException("redis unavailable")).when(redis)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThrows(IllegalStateException.class, lease::acquireOrRenew);
        assertFalse(lease.isValid());
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        lease.release();
        assertFalse(lease.isValid());
    }

    @Test
    void shouldNotAcquireAlreadyOwnedLease() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        assertFalse(lease.acquireOrRenew());
        assertFalse(lease.isValid());
    }

    @Test
    void shouldNotReviveOldGenerationAfterExpiryAndRenewal() {
        acquire();
        long generation = lease.generation();
        time.addAndGet(Duration.ofSeconds(55).toNanos());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        assertTrue(lease.acquireOrRenew());
        assertNotEquals(generation, lease.generation());
    }

    @Test
    void shouldPermanentlyRetireBeforeRedisReleaseCompletes() {
        acquire();
        long generation = lease.generation();
        lease.retire();
        assertFalse(lease.isValid());
        assertNull(lease.ownerToken());
        assertFalse(lease.acquireOrRenew());
        assertNotEquals(generation, lease.generation());
    }

    @Test
    void shouldInvalidateOnlyCallbacksWithoutDroppingOwnership() {
        acquire();
        String token = lease.ownerToken();
        long generation = lease.generation();
        lease.invalidateCallbacks();
        assertTrue(lease.isValid());
        assertEquals(token, lease.ownerToken());
        assertNotEquals(generation, lease.generation());
    }
}
