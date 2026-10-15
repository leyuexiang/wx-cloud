package cn.iocoder.wx.module.dimensions.consumer.business;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.wx.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @desc 处理电气相关任务消息的消费者
 * @author akon
 * @create 2024-02-29
 */
@Component
public class ElectricTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.ELECTRIC_CENTER_TOPIC;

    /**
     * 消费建筑相关的 Kafka 消息
     * @param message 接收到的 Kafka 消息
     */
    @KafkaListener(topics = CURRENT_TOPIC, groupId = "electric-consumer-group")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}