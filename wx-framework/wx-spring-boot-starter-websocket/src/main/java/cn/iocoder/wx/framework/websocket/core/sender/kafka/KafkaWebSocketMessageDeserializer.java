package cn.iocoder.wx.framework.websocket.core.sender.kafka;

import org.apache.kafka.common.serialization.Deserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class KafkaWebSocketMessageDeserializer implements Deserializer<KafkaWebSocketMessage> {
    // 两端共用 Kafka 传输配置，确保会话编号和消息内容完整往返。
    private final ObjectMapper objectMapper = KafkaWebSocketMessageSerializer.createTransportMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 配置方法，可根据需要实现
    }

    @Override
    public KafkaWebSocketMessage deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, KafkaWebSocketMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize KafkaWebSocketMessage", e);
        }
    }

    @Override
    public void close() {
        // 关闭方法，可根据需要实现
    }
}
