package cn.iocoder.wx.module.dimensions.iot;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** 核验传给 Lua 的全序版本，覆盖纳秒、秒边界、平局与 Instant 极值。 */
class IotStateOrderingTest {
    private String version(Instant time, String id) {
        return RedisIotStateStore.version(IotStateProjectionTest.event(time, id));
    }

    @Test
    void shouldPreserveNanosecondAndSecondOrdering() {
        Instant time = Instant.parse("2026-10-19T00:00:00.999999999Z");
        assertTrue(version(time, "z").compareTo(version(time.plusNanos(1), "a")) < 0);
        assertTrue(version(time.minusNanos(1), "z").compareTo(version(time, "a")) < 0);
    }

    @Test
    void shouldOrderEqualTimesDeterministicallyAndRecognizeDuplicate() {
        Instant time = Instant.parse("2026-10-19T00:00:00Z");
        assertTrue(version(time, "event-a").compareTo(version(time, "event-b")) < 0);
        assertEquals(version(time, "event-a"), version(time, "event-a"));
    }

    @Test
    void shouldCoverInstantRangeWithoutNumericPrecisionLoss() {
        assertTrue(version(Instant.MIN, "z").compareTo(version(Instant.EPOCH, "a")) < 0);
        assertTrue(version(Instant.EPOCH, "z").compareTo(version(Instant.MAX, "a")) < 0);
    }

    @Test
    void shouldIsolateTenantsAndDelimiterContainingIdentities() {
        assertNotEquals(RedisIotStateStore.key(7L, "a:b", "c"), RedisIotStateStore.key(7L, "a", "b:c"));
        assertNotEquals(RedisIotStateStore.key(7L, "设备", "温度"), RedisIotStateStore.key(8L, "设备", "温度"));
        assertThrows(IllegalArgumentException.class, () -> RedisIotStateStore.key(null, "a", "b"));
    }
}
