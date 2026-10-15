package cn.iocoder.wx.module.dimensions.consumer;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.wx.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @author akon
 * @create 2024-05-20
 * @description 定时任务发送到kafka的消息，任务消息的消费者
 */
@Component
public class CommonUniversalTaskMessageConsumer extends AbstractTaskMessageConsumer {

    @KafkaListener(topics = KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC, groupId = "common-universal-group")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        super.consumeMessage(message, KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC);
    }
}
