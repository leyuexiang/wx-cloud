package cn.iocoder.wx.module.dimensions.iot;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 使用真实 Kafka 错误处理器核验死信确认边界，发送结果通过 Future 控制。 */
@SuppressWarnings({"unchecked", "rawtypes"})
class IotStateDeadLetterTest {
    private KafkaOperations<String, String> sender() {
        KafkaOperations<String, String> sender = mock(KafkaOperations.class);
        ProducerFactory<String, String> factory = mock(ProducerFactory.class);
        when(sender.getProducerFactory()).thenReturn(factory);
        when(factory.getConfigurationProperties()).thenReturn(Map.of(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1000));
        return sender;
    }

    @Test
    void shouldQuarantineOriginalTextBeforeAllowingOffsetAdvance() {
        var sender = sender();
        when(sender.send(any(ProducerRecord.class))).thenAnswer(invocation -> CompletableFuture.completedFuture(
                new SendResult<>(invocation.getArgument(0), mock(RecordMetadata.class))));
        var handler = IotStateProjectionConfiguration.createErrorHandler(sender, "iot-state.dlt");
        var record = new ConsumerRecord<>("twin.event.v1", 0, 42L, "event-1", "{malformed}");
        assertTrue(handler.handleOne(new IotTelemetryEventCodec.InvalidEventException("格式错误"),
                record, mock(Consumer.class), mock(MessageListenerContainer.class)));
        ArgumentCaptor<ProducerRecord<String, String>> capture = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(sender).send(capture.capture());
        assertEquals("iot-state.dlt", capture.getValue().topic());
        assertEquals("{malformed}", capture.getValue().value());
        assertEquals("event-1", capture.getValue().key());
        assertTrue(handler.isAckAfterHandle());
    }

    @Test
    void shouldNotHandleRecordWhenDeadLetterBrokerRejectsSend() {
        var sender = sender();
        when(sender.send(any(ProducerRecord.class))).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("隔离主题不可用")));
        var handler = IotStateProjectionConfiguration.createErrorHandler(sender, "iot-state.dlt");
        var record = new ConsumerRecord<>("twin.event.v1", 0, 42L, "event-1", "{malformed}");
        assertFalse(handler.handleOne(new IotTelemetryEventCodec.InvalidEventException("格式错误"),
                record, mock(Consumer.class), mock(MessageListenerContainer.class)));
    }
}
