package cn.iocoder.jtyf.module.dimensions.consumer.business;

import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @desc 处理人员相关任务消息的消费者
 * @author wtj
 * @create 2025-05-13
 */
@Component
public class EnvProTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.ENVIRONMENTAL_PROTECTION_CENTER_TOPIC;

    @KafkaListener(topics = CURRENT_TOPIC, groupId = "environmental-protection-consumer-group")
    public void consumeMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}
