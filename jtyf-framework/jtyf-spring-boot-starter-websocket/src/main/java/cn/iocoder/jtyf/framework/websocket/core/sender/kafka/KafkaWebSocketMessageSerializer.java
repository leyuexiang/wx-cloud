package cn.iocoder.jtyf.framework.websocket.core.sender.kafka;

import org.apache.kafka.common.serialization.Serializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class KafkaWebSocketMessageSerializer implements Serializer<KafkaWebSocketMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // 配置方法，可根据需要实现
    }

    @Override
    public byte[] serialize(String topic, KafkaWebSocketMessage data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize KafkaWebSocketMessage", e);
        }
    }

    @Override
    public void close() {
        // 关闭方法，可根据需要实现
    }
}
