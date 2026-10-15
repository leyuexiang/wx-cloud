package cn.iocoder.jtyf.module.dimensions.consumer.common;

import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.jtyf.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.listener.SendMessageEvent;
import cn.iocoder.jtyf.module.dimensions.listener.SendQueueManager;
import cn.iocoder.jtyf.module.dimensions.service.TaskMessageService;
import cn.iocoder.jtyf.module.dimensions.websocket.enums.MessageType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author akon
 * @desc 基础类，用于处理任务消息消费相关逻辑
 * @create 2024-02-29
 */
@Component
@Slf4j
public abstract class AbstractTaskMessageConsumer {

    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    @Resource
    protected TaskMessageService taskMessageService;
    @Resource
    protected StringRedisTemplate stringRedisTemplate;
    @Resource
    protected ApplicationEventPublisher eventPublisher;
    @Resource
    protected SendQueueManager sendQueueManager;

    public void consumeMessageToRedis(KafkaWebSocketMessage message, String topicName) {
        if (message == null) {
            log.warn("Received kafka message is null, businessModule: {}", topicName);
            return;
        }

        log.info("Topic：{}, Received kafka 原始消息: {}", topicName, message);
        String businessKey = BusinessRedisConstants.DEVICE_INFO + ":" + topicName;
        try {
            JSONObject convertMessage = convertMessageToJson(message, topicName);
            String convertMessageStr = JSONUtil.toJsonStr(convertMessage);

            // 将kafka的消息放入到redis内, 按设备存入一条最新的；按业务存入list顺序在右，拿到当前存入的数量；
            writeDeviceToRedisLatest(convertMessage.get("data"), convertMessageStr);
            long size = writeMessageToRedis(topicName, businessKey, convertMessageStr);

            // 发布当前写入redis的事件，
            eventPublisher.publishEvent(new SendMessageEvent(this, topicName,  businessKey, size));

            boolean result = taskMessageService.saveMessage(convertMessageStr);
            log.info("消息保存: {}", result ? "成功" : "失败");
        } catch (Exception e) {
            log.error("Error processing kafka message for businessModule: {}", businessKey, e);
        }
    }

    /**
     * 写入最新的一条设备数据到redis
     * @param data kafka消息体数据
     * @param convertMessageStr 业务消息数据
     */
    protected void writeDeviceToRedisLatest(Object data, String convertMessageStr) {
        if (data instanceof JSONObject dataJson) {
            stringRedisTemplate.opsForHash().put(BusinessRedisConstants.DEVICE_INFO + ":current",
                            dataJson.get(BusinessRedisConstants.DEVICE_CODE), convertMessageStr);
        }
    }

    private long writeMessageToRedis(String topicName, String businessKey, String convertMessageStr) {
        try {
            // 这里加锁的原因，是为了防止在进行U3D session连接时全量发送读错数据，需要等它读完，再写入
            sendQueueManager.lockTopic(topicName);

            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(businessKey))) {
                stringRedisTemplate.opsForList().set(businessKey, 0, convertMessageStr);
                return 0L;
            }
            return Optional.ofNullable(stringRedisTemplate.opsForList().rightPush(businessKey, convertMessageStr))
                    .orElseThrow(() -> new RuntimeException("存入redis异常，没有获取到当前kafka消息的list索引"));
        } finally {
            sendQueueManager.unlockTopic(topicName);
        }
    }

    public void consumeMessage(KafkaWebSocketMessage message, String businessModuleValue) {
        if (message == null) {
            log.warn("Received kafka message is null, businessModule: {}", businessModuleValue);
            return;
        }

        log.info("Topic：{}, Received kafka 原始消息: {}", businessModuleValue, message);
        try {
            JSONObject convertMessage = convertMessageToJson(message, businessModuleValue);
            String convertMessageStr = JSONUtil.toJsonStr(convertMessage);

            getSelf().sendAsyncMessageToAdmin(MessageType.TASK_EVENT_RECEIVE.getType(), convertMessageStr);

            boolean result = taskMessageService.saveMessage(convertMessageStr);
            log.info("消息保存: {}", result ? "成功" : "失败");
        } catch (Exception e) {
            log.error("Error processing kafka message for businessModule: {}", businessModuleValue, e);
        }
    }

    protected JSONObject convertMessageToJson(KafkaWebSocketMessage message, String businessModuleValue) {
        JSONObject convertMessage = JSONUtil.parseObj(message);
        convertMessage.set("businessModule", businessModuleValue);
        JSONObject data = JSONUtil.parseObj(convertMessage.get("data"));
        if (data == null) {
            log.warn("Data in received kafka message is null, message: {}, businessModule: {}", message, businessModuleValue);
            return convertMessage;
        }
        String externalTaskId = data.getStr("command") + "#" + data.getStr(BusinessRedisConstants.DEVICE_CODE);
        data.set("taskId", externalTaskId);
        convertMessage.set("data", data);
        return convertMessage;
    }

    protected String getDataHashKey(Object data) {
        if (data instanceof JSONObject dataJson) {
            Object taskId = dataJson.get("taskId");
            if (taskId instanceof String dataHashKey) {
                return dataHashKey;
            }
        }
        throw new RuntimeException("获取不到taskId");
    }

    @Async
    public void sendAsyncMessageToAdmin(String messageType, String content) {
        try {
            webSocketMessageSender.send(messageType, content);
            log.info("原始消息处理后，转发给websocket: {}", content);
        } catch (Exception e) {
            log.error("Error sending message to admin, messageType: {}, content: {}", messageType, content, e);
        }
    }

    protected AbstractTaskMessageConsumer getSelf() {
        return SpringUtil.getBean(getClass());
    }

    /**
     * 获取业务流程状态redis key值
     * @param businessKey 业务流程redis key值
     * @return 业务流程状态redis key值
     */
    protected String getBusinessStatusKey(String businessKey) {
        return businessKey + ":workflowStatus";
    }
}