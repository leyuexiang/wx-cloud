package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.dimensions.controller.admin.vo.IotStateRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖跨服务时间/精度、过期重放、时钟偏差和身份错误，不连接生产依赖。 */
class IotStateProjectionTest {
    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00.123456789Z");
    private final IotStateStore store = mock(IotStateStore.class);
    private final IotTelemetryEventCodec codec = new IotTelemetryEventCodec();
    private final IotStateProjectionProperties properties = new IotStateProjectionProperties();
    private final IotStateProjectionService service = new IotStateProjectionService(
            store, codec, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    static TwinTelemetryEvent event(Instant time, String eventId) {
        return TwinTelemetryEvent.builder().schemaVersion("1.0").eventId(eventId).eventTime(time)
                .tenantId(7L).deviceId("device-1").pointCode("temperature").valueType(IotValueType.NUMBER)
                .value(new BigDecimal("123456789.123456789123456789")).build();
    }

    @Test
    void shouldPreserveKafkaNumericTimestampAndDecimalPrecision() {
        TwinTelemetryEvent original = event(NOW, "event-1");
        try (JsonSerializer<TwinTelemetryEvent> serializer = new JsonSerializer<>()) {
            String payload = new String(serializer.serialize("twin.event.v1", original), StandardCharsets.UTF_8);
            when(store.putLatest(any(), anyLong())).thenReturn(true);
            assertTrue(service.project(payload));
            verify(store).putLatest(argThat(decoded -> original.getEventTime().equals(decoded.getEventTime())
                    && original.getValue().equals(decoded.getValue())), eq(Duration.ofDays(1).toMillis()));
        }
    }

    @Test
    void shouldKeepNanosecondsInCachedSnapshot() {
        TwinTelemetryEvent original = event(NOW, "event-1");
        TwinTelemetryEvent restored = codec.decode(codec.encode(original));
        assertEquals(original.getEventTime(), restored.getEventTime());
        assertEquals(original.getValue(), restored.getValue());
        // 接口即使沿用三维服务的全局毫秒序列化，也不能抹去投影排序所依赖的纳秒。
        assertEquals(NOW.toString(), IotStateRespVO.from(restored).eventTime());
    }

    @Test
    void shouldNeverReviveExpiredReplayOrWriteTombstone() {
        TwinTelemetryEvent expired = event(NOW.minus(Duration.ofDays(1)), "event-old");
        expired.setReplay(true);
        assertFalse(service.project(codec.encode(expired)));
        verifyNoInteractions(store);
    }

    @Test
    void shouldExpireDelayedEventsRelativeToTheirEventTime() {
        service.project(codec.encode(event(NOW.minusSeconds(60), "event-delayed")));
        verify(store).putLatest(any(), eq(Duration.ofDays(1).minusSeconds(60).toMillis()));
    }

    @Test
    void shouldQuarantineExcessiveFutureTimestamp() {
        assertThrows(IotTelemetryEventCodec.InvalidEventException.class,
                () -> service.project(codec.encode(event(NOW.plusSeconds(301), "event-future"))));
        verifyNoInteractions(store);
    }

    @Test
    void shouldRejectMissingIdentityAndUnknownVersionBeforeWriting() {
        List<Consumer<TwinTelemetryEvent>> mutations = List.of(
                e -> e.setTenantId(null), e -> e.setTenantId(-1L), e -> e.setDeviceId(" "),
                e -> e.setPointCode(null), e -> e.setEventId(null), e -> e.setEventTime(null),
                e -> e.setValueType(null), e -> e.setSchemaVersion("2.0"));
        for (Consumer<TwinTelemetryEvent> mutation : mutations) {
            TwinTelemetryEvent invalid = event(NOW, "event-1");
            mutation.accept(invalid);
            assertThrows(IotTelemetryEventCodec.InvalidEventException.class,
                    () -> service.project(codec.encode(invalid)));
        }
        assertThrows(IotTelemetryEventCodec.InvalidEventException.class, () -> service.project("null"));
        verifyNoInteractions(store);
    }

    @Test
    void shouldRejectMalformedPayloadWithoutExposingItsContent() {
        var error = assertThrows(IotTelemetryEventCodec.InvalidEventException.class,
                () -> service.project("{\"private-reading\":"));
        assertFalse(error.toString().contains("private-reading"));
        assertNull(error.getCause());
        assertThrows(IotTelemetryEventCodec.InvalidEventException.class,
                () -> service.project(codec.encode(event(NOW, "event-1")) + " {}"));
        verifyNoInteractions(store);
    }

    @Test
    void shouldLetStorageFailuresReachKafkaErrorHandler() {
        var failure = new RedisConnectionFailureException("不可连接");
        when(store.putLatest(any(), anyLong())).thenThrow(failure);
        assertSame(failure, assertThrows(RedisConnectionFailureException.class,
                () -> service.project(codec.encode(event(NOW, "event-1")))));
    }

    @Test
    void shouldRejectFractionalTenantInsteadOfSilentlyTruncatingIt() {
        String payload = codec.encode(event(NOW, "event-1")).replace("\"tenantId\":7", "\"tenantId\":7.5");
        assertThrows(IotTelemetryEventCodec.InvalidEventException.class, () -> service.project(payload));
        verifyNoInteractions(store);
    }
}
