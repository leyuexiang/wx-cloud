package cn.iocoder.jtyf.module.dimensions.consumer.business;

import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author akon
 * @create 2024-02-29
 * @description 处理通风相关任务消息的消费者
 */
@Component
public class VentilationTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.VENTILATION_CENTER_TOPIC;

    @KafkaListener(topics = CURRENT_TOPIC, groupId = "ventilation-consumer-group")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}
