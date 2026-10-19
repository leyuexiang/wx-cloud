package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 直接使用 Kafka 默认编解码器核验跨服务契约，避免依赖 Web 层的构造参数模块。 */
class IotTelemetryContractTest {

    @Test
    void shouldReadProducerPayloadWithoutWebObjectMapper() {
        TwinTelemetryEvent event = TwinTelemetryEvent.builder().eventId("event-1").schemaVersion("1.0")
                .eventTime(Instant.parse("2026-10-19T00:00:00.123456789Z"))
                .tenantId(7L).deviceId("device-1").pointCode("temperature")
                .valueType(IotValueType.NUMBER).value(new BigDecimal("26.125")).build();
        try (JsonSerializer<TwinTelemetryEvent> serializer = new JsonSerializer<>();
             JsonDeserializer<TwinTelemetryEvent> deserializer = new JsonDeserializer<>(
                     TwinTelemetryEvent.class, JacksonUtils.enhancedObjectMapper(), false)) {
            TwinTelemetryEvent restored = deserializer.deserialize("twin.event.v1",
                    serializer.serialize("twin.event.v1", event));
            assertEquals(event.getEventId(), restored.getEventId());
            assertEquals(event.getEventTime(), restored.getEventTime());
            assertEquals(event.getTenantId(), restored.getTenantId());
        }
    }
}
