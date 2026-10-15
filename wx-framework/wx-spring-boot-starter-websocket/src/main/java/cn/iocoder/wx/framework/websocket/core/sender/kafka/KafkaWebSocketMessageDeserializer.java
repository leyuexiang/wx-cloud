package cn.iocoder.wx.framework.websocket.core.sender.kafka;

import org.apache.kafka.common.serialization.Deserializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class KafkaWebSocketMessageDeserializer implements Deserializer<KafkaWebSocketMessage> {
    private final ObjectMapper objectMapper;

    public KafkaWebSocketMessageDeserializer() {
        this.objectMapper = new ObjectMapper();
        // 配置Jackson忽略未识别的字段
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

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
