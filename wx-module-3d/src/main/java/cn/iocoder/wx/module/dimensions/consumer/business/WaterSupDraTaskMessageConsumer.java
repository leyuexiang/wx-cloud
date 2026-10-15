package cn.iocoder.wx.module.dimensions.consumer.business;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.wx.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.wx.module.dimensions.consumer.common.KafkaTopicsConstants;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @desc 处理给排水相关任务消息的消费者
 * @author akon
 * @create 2024-02-29
 */
@Component
public class WaterSupDraTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC;

    @KafkaListener(topics = CURRENT_TOPIC, groupId = "water-supply-and-drainage-consumer-group")
    public void consumeMessage(KafkaWebSocketMessage message) {
        super.consumeMessageToRedis(message, CURRENT_TOPIC);
    }
}
