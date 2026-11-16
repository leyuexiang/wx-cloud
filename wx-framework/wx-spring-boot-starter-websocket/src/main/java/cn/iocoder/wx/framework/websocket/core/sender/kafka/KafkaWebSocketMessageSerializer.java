package cn.iocoder.wx.framework.websocket.core.sender.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaWebSocketMessageSerializer implements Serializer<KafkaWebSocketMessage> {

    private final ObjectMapper objectMapper = createTransportMapper();

    static ObjectMapper createTransportMapper() {
        return new ObjectMapper()
                .addMixIn(KafkaWebSocketMessage.class, TransportFields.class)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** 仅 Kafka 编解码读写路由字段，旧业务对象继续使用原有的 JSON 形状。 */
    private static class TransportFields {
        @JsonIgnore(false)
        @JsonProperty("sessionId")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String sessionId;
        @JsonIgnore(false)
        @JsonProperty("userType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Integer userType;
        @JsonIgnore(false)
        @JsonProperty("userId")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Long userId;
        @JsonIgnore(false)
        @JsonProperty("messageType")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String messageType;
        @JsonIgnore(false)
        @JsonProperty("messageContent")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String messageContent;
    }

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
