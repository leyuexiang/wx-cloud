package cn.iocoder.jtyf.module.dimensions.consumer.business;

import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author akon
 * @desc 处理建筑相关任务消息的消费者
 * @create 2024-02-29
 */
@Component
@Slf4j
public class BuildingTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.BUILDING_CENTER_TOPIC;

    /**
     * 消费建筑相关的 Kafka 消息
     *
     * @param message 接收到的 Kafka 消息
     */
    @KafkaListener(topics = CURRENT_TOPIC, groupId = "building-consumer-group")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}