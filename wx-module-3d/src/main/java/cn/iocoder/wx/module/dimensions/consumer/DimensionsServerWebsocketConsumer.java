package cn.iocoder.wx.module.dimensions.consumer;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.wx.module.dimensions.websocket.enums.MessageType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author akon
 * @desc 处理组态发送给U3D相关任务消息的消费者
 * @create 2024-02-29
 */
@Component
public class DimensionsServerWebsocketConsumer extends AbstractTaskMessageConsumer {

    /**
     * 消费转运相关的 Kafka 消息
     *
     * @param message 接收到的 Kafka 消息
     */
    @KafkaListener(
            topics = "${wx.websocket.sender-kafka.topic}",
            // 在 Group 上，使用 UUID 生成其后缀。这样，启动的 Consumer 的 Group 不同，以达到广播消费的目的
            groupId = "${wx.websocket.sender-kafka.consumer-group}" + "-" + "#{T(java.util.UUID).randomUUID()}")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        JSONObject convertMessage = JSONUtil.parseObj(message);
        convertMessage.set("businessModule", "dimensions-server-websocket");

        JSONObject dataObj = JSONUtil.parseObj(message.getMessageContent());
        convertMessage.set("data", dataObj.get("text"));

        convertMessage.set("type", "0");
        convertMessage.set("version", "0");
        String convertMessageStr = JSONUtil.toJsonStr(convertMessage);
        super.getSelf().sendAsyncMessageToAdmin(MessageType.TASK_EVENT_RECEIVE.getType(), convertMessageStr);
    }
}