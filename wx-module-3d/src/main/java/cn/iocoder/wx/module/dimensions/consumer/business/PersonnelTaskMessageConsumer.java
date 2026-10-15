package cn.iocoder.wx.module.dimensions.consumer.business;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.wx.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @desc 处理人员相关任务消息的消费者
 * @author wtj
 * @create 2025-05-13
 */
@Component
public class PersonnelTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.PERSONNEL_CENTER_TOPIC;

    @KafkaListener(topics = CURRENT_TOPIC, groupId = "personnel-consumer-group")
    public void consumeMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}
