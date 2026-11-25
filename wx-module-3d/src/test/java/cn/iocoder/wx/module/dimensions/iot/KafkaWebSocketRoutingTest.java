package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageDeserializer;
import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSerializer;
import cn.iocoder.wx.module.dimensions.consumer.DimensionsServerWebsocketConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class KafkaWebSocketRoutingTest {

    private static final String TOPIC = "dimensions-websocket";
    private final KafkaWebSocketMessageSerializer serializer = new KafkaWebSocketMessageSerializer();
    private final KafkaWebSocketMessageDeserializer deserializer = new KafkaWebSocketMessageDeserializer();

    @Test
    void keepsRoutingDataOnKafkaWithoutExposingItInOrdinaryJson() throws Exception {
        // 覆盖会话和用户两种路由，防止任一字段在 Kafka 往返时丢失。
        KafkaWebSocketMessage sessionMessage = new KafkaWebSocketMessage()
                .setSessionId("session-b").setMessageType("iot-state-update").setMessageContent("{\"value\":true}");
        KafkaWebSocketMessage userMessage = new KafkaWebSocketMessage()
                .setUserType(2).setUserId(100L).setMessageType("notice").setMessageContent("{}");

        for (KafkaWebSocketMessage message : new KafkaWebSocketMessage[]{sessionMessage, userMessage}) {
            assertThat(deserializer.deserialize(TOPIC, serializer.serialize(TOPIC, message))).isEqualTo(message);
            assertThat(new ObjectMapper().valueToTree(message).fieldNames())
                    .toIterable().containsExactlyInAnyOrder("version", "type", "data");
        }
    }

    @Test
    void keepsBusinessPayloadAndDoesNotForwardTargetedMessagesToTheLegacyBroadcast() {
        // 旧业务对象保持原有 JSON，含定向标记的消息不能进入全会话广播。
        byte[] existing = "{\"version\":\"0\",\"type\":\"0\",\"data\":{\"value\":true}}".getBytes(StandardCharsets.UTF_8);
        KafkaWebSocketMessage businessMessage = deserializer.deserialize(TOPIC, existing);
        assertThat(serializer.serialize(TOPIC, businessMessage)).isEqualTo(existing);

        var consumer = spy(new DimensionsServerWebsocketConsumer());
        var message = new KafkaWebSocketMessage().setSessionId("session-b")
                .setMessageType("iot-state-update").setMessageContent("{\"text\":\"private\"}");
        consumer.consumeBuildingMessage(message);
        verify(consumer, times(1)).consumeBuildingMessage(message);
        verifyNoMoreInteractions(consumer);
    }
}
