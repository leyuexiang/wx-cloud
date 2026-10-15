package cn.iocoder.jtyf.module.dimensions.consumer.business;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.AbstractTaskMessageConsumer;
import cn.iocoder.jtyf.module.dimensions.consumer.common.BusinessRedisConstants;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.domain.dto.RedisDeviceInfoDTO;
import cn.iocoder.jtyf.module.dimensions.listener.SendTransportMessageEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * @desc 处理转运相关任务消息的消费者
 * @author akon
 * @create 2024-02-29
 */
@Component
@Slf4j
public class TransportationTaskMessageConsumer extends AbstractTaskMessageConsumer {

    private static final String CURRENT_TOPIC = KafkaTopicsConstants.TRANSPORTATION_CENTER_TOPIC;
    /**
     * 消费转运相关的 Kafka 消息
     * @param message 接收到的 Kafka 消息
     */
    @KafkaListener(topics = CURRENT_TOPIC, groupId = "transportation-consumer-group")
    public void consumeBuildingMessage(KafkaWebSocketMessage message) {
        if (message == null) {
            log.warn("Received kafka message is null, businessModule: {}", CURRENT_TOPIC);
            return;
        }
        this.consumeMessageToRedis(message, CURRENT_TOPIC);
    }

    @Override
    public void consumeMessageToRedis(KafkaWebSocketMessage message, String topicName) {
        log.info("Topic：{}, Received kafka 原始消息: {}", topicName, message);
        String businessKey = BusinessRedisConstants.DEVICE_INFO + ":" + topicName;
        try {
            JSONObject convertMessage = convertMessageToJson(message, topicName);
            String convertMessageStr = JSONUtil.toJsonStr(convertMessage);

            // 当前的消息状态是结束，则当前业务流程结束
            String messageStatus = getMessageStatus(convertMessage.get("data"));
            if (BusinessRedisConstants.FINISH.equals(messageStatus)) {
                stringRedisTemplate.opsForValue().set(getBusinessStatusKey(businessKey), BusinessRedisConstants.FINISH);
                return;
            }

            // 将kafka的消息放入到redis内, 按设备存入一条最新的；按任务，存一条流程内设备最新的
            String dataHashKey = getDataHashKey(convertMessage.get("data"));
            writeDeviceToRedisLatest(convertMessage.get("data"), convertMessageStr);
            writeTransportMessageToRedis(topicName, businessKey, dataHashKey, convertMessageStr);

            // 发布当前写入redis的事件，
            eventPublisher.publishEvent(new SendTransportMessageEvent(this, topicName, businessKey, dataHashKey));

            boolean result = taskMessageService.saveMessage(convertMessageStr);
            log.info("消息保存: {}", result ? "成功" : "失败");
        } catch (Exception e) {
            log.error("Error processing kafka message for businessModule: {}", businessKey, e);
        }
    }

    private void writeTransportMessageToRedis(String topicName, String businessKey, String dataHashKey,
                                        String convertMessageStr) {
        try {
            // 这里加锁的原因，是为了防止在进行U3D session连接时全量发送读错数据，需要等它读完，再写入
            sendQueueManager.lockTopic(topicName);

            // 如果没有业务流程相关信息，则新写入信息
            String businessStatusKey = getBusinessStatusKey(businessKey);
            String businessStatus = stringRedisTemplate.opsForValue().get(businessStatusKey);
            if (StringUtils.isEmpty(businessStatus)) {
                stringRedisTemplate.opsForValue().set(businessStatusKey, BusinessRedisConstants.START);
                writeTransportBusiness(businessKey, dataHashKey, convertMessageStr);
                return;
            }

            // 当前的消息状态是开始, 当前业务流程状态也是开始，则新写入一条数据
            if (BusinessRedisConstants.START.equals(businessStatus)) {
                writeTransportBusiness(businessKey, dataHashKey, convertMessageStr);
                return;
            }

            // 当前业务流程状态是结束，则应该清除掉当前流程数据，再新写入新的数据
            stringRedisTemplate.delete(businessKey);
            stringRedisTemplate.opsForValue().set(businessStatusKey, BusinessRedisConstants.START);
            writeTransportBusiness(businessKey, dataHashKey, convertMessageStr);
        } finally {
            sendQueueManager.unlockTopic(topicName);
        }
    }

    private void writeTransportBusiness(String businessKey, String dataHashKey, String data) {
        RedisDeviceInfoDTO redisDeviceInfo = new RedisDeviceInfoDTO()
                .setData(data).setStatus(BusinessRedisConstants.RECEIVE);
        stringRedisTemplate.opsForHash().put(businessKey, dataHashKey, JSONUtil.toJsonStr(redisDeviceInfo));
    }

    /**
     * 获取消息体内的 当前流程是否结束
     * @param data 消息体内数据
     * @return 流程状态
     */
    private String getMessageStatus(Object data) {
        if (data instanceof JSONObject dataJson) {
            if (dataJson.get("isEnd") instanceof Boolean isEnd) {
                return isEnd ? BusinessRedisConstants.FINISH : BusinessRedisConstants.START;
            }
        }
        return BusinessRedisConstants.START;
    }
}