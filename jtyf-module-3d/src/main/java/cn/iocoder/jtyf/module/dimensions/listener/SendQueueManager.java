package cn.iocoder.jtyf.module.dimensions.listener;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.jtyf.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.jtyf.module.dimensions.consumer.common.BusinessRedisConstants;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsEnum;
import cn.iocoder.jtyf.module.dimensions.domain.dto.RedisDeviceInfoDTO;
import cn.iocoder.jtyf.module.dimensions.websocket.enums.MessageType;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: cheYu
 * @desc: 发送业务流程数据管理器，所有从redis拿取数据，发送websocket的业务流程，都通过此来发送；
 *        实现每个业务流程，对应一个队列，所有发送任务都进行出队入队，保证全局发送顺序的一致性
 * @create: 2025-06-19 10:48
 */
@Component
@Slf4j
public class SendQueueManager {
    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Value("${jtyf.message-data.speed}")
    private String speedRate = "1";

    private static final Map<String, Queue<String>> queueMap = new HashMap<>();
    private static final Map<String, ReentrantLock> lockMap = new HashMap<>();
    private static final Map<String, AtomicBoolean> shutFlagMap = new ConcurrentHashMap<>();

    private final Set<Object> tokenSet = new ConcurrentHashSet<>();

    private final ReentrantLock tokenLock = new ReentrantLock();

    private static final List<String> speedList = List.of("mainCarSpeed", "subCarSpeed", "liftingLoweringSpeed",
            "rotationParameters_speed", "swingParameters_speed", "liftingParameters_speed", "hookPoint1_speed",
            "hookPoint2_speed", "speed");


    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // 初始化每个topic需要的队列，与相应的锁
        for (KafkaTopicsEnum kafkaTopicsEnum : KafkaTopicsEnum.getNonPrefixTopics()) {
            queueMap.put(kafkaTopicsEnum.getTopicName(), new ConcurrentLinkedQueue<>());
            lockMap.put(kafkaTopicsEnum.getTopicName(), new ReentrantLock(true));
            shutFlagMap.put(kafkaTopicsEnum.getTopicName(), new AtomicBoolean(false));
        }
    }

    /**
     * 获取可发送的token，如果当前处于停止发放token时，获取为null
     * @return token
     */
    public Object getToken(String topicName) {
        try {
            tokenLock.lock();
            // 当前处于停止发放token状态
            if (shutFlagMap.get(topicName).get()) {
                return null;
            }
            Object token = new Object();
            tokenSet.add(token);
            return token;
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * 中断正在准备发送的任务，并停止发放token
     */
    public void interruptRunningSend() {
        try {
            tokenLock.lock();

            for (KafkaTopicsEnum kafkaTopicsEnum : KafkaTopicsEnum.getNonPrefixTopics()) {
                shutFlagMap.get(kafkaTopicsEnum.getTopicName()).set(true);
            }
            tokenSet.clear();
        } finally {
            tokenLock.unlock();
        }
    }

    private void restoreToken(String topicName) {
        shutFlagMap.get(topicName).set(false);
    }

    private boolean isInvalidToken(Object token) {
        return tokenSet.contains(token);
    }

    /**
     * 通用，提交发送全量流程消息
     */
    public void submitSendAll() {
        // 获取Redis里面的所有topic数据了
        for (KafkaTopicsEnum kafkaTopicsEnum : KafkaTopicsEnum.getNonPrefixTopics()) {
            String businessTopic = BusinessRedisConstants.DEVICE_INFO + ":" + kafkaTopicsEnum.getTopicName();
            try {
                // 正在全量读时，不允许当前topic写入redis，
                lockTopic(kafkaTopicsEnum.getTopicName());

                List<String> messageStrList = getMessageList(businessTopic);
                if (CollectionUtils.isEmpty(messageStrList)) {
                    log.info("当前业务[{}]在redis内数据为空，无需发送。", businessTopic);
                    continue;
                }
                // 每读取完一个topic的数据，就将其提交异步发送
                CompletableFuture.runAsync(() -> submitSendQueue(kafkaTopicsEnum.getTopicName(), messageStrList));
            } finally {
                restoreToken(kafkaTopicsEnum.getTopicName());
                unlockTopic(kafkaTopicsEnum.getTopicName());
            }
        }
    }

    /**
     * 通用 提交发送单个流程消息，
     *
     * @param event 发送消息给U3D的事件；
     * @param token token，用于判断当前是否可以发送消息
     */
    public void submitSend(SendMessageEvent event, Object token) {
        log.info("Topic：{}, Redis数据列表索引: [{}]", event.getBusinessKey(), event.getListIndex());
        String sourceStr = stringRedisTemplate.opsForList().index(event.getBusinessKey(), event.getListIndex());
        if (StringUtils.isEmpty(sourceStr)) {
            log.error("当前业务[{}]未在redis索引[{}]处获取相应数据。", event.getBusinessKey(), event.getListIndex());
            return;
        }
        submitSendQueue(event.getTopicName(), sourceStr, token);
    }

    /**
     * 提交发送转运流程消息
     *
     * @param event 发送消息给U3D 转运流程的事件
     * @param token token，用于判断当前是否可以发送消息
     */
    public void submitSendTransport(SendTransportMessageEvent event, Object token) {
        log.info("Topic：{}, Redis数据表key: [{}]", event.getBusinessKey(), event.getDataHashKey());
        Object sourceStr = stringRedisTemplate.opsForHash().get(event.getBusinessKey(), event.getDataHashKey());
        if (sourceStr == null) {
            log.error("当前业务[{}]未在Redis数据表key[{}]处获取相应数据。", event.getBusinessKey(), event.getDataHashKey());
            return;
        }
        // 转运流程特殊处理，内部存储了发送状态，并修改数据状态为 已发送；
        RedisDeviceInfoDTO redisDeviceInfo = JSONUtil.toBean(sourceStr.toString(), RedisDeviceInfoDTO.class);
        updateRedisStatus(event.getBusinessKey(), event.getDataHashKey(), redisDeviceInfo);

        submitSendQueue(event.getTopicName(), redisDeviceInfo.getData(), token);
    }

    private List<String> getMessageList(String businessTopic) {
        // 提取出缓存内的消息， 转运流程是map存储
        if (businessTopic.contains(KafkaTopicsConstants.TRANSPORTATION_CENTER_TOPIC)) {
            return getTransportMessageList(businessTopic);
        }
        return stringRedisTemplate.opsForList().range(businessTopic, 0, -1);
    }

    private List<String> getTransportMessageList(String businessTopic) {
        Map<Object, Object> redisSourceMap = stringRedisTemplate.opsForHash().entries(businessTopic);

        // 转运流程处理，提取出缓存内的消息
        List<String> messageStrList = new ArrayList<>(redisSourceMap.size());
        redisSourceMap.forEach((dataHashKey, dataValue) -> {
            RedisDeviceInfoDTO redisDeviceInfo = JSONUtil.toBean(dataValue.toString(), RedisDeviceInfoDTO.class);
            // 将接收状态，调整为发送状态
            if (BusinessRedisConstants.RECEIVE.equals(redisDeviceInfo.getStatus())) {
                updateRedisStatus(businessTopic, dataHashKey.toString(), redisDeviceInfo);
            }

            // 需要给3D标识，当前消息是全量消息，还是增量消息
            JSONObject convertMessage = JSONUtil.parseObj(redisDeviceInfo.getData());
            convertMessage.set("sendType", "full");
            messageStrList.add(convertMessage.toString());
        });
        return messageStrList;
    }

    private String handleSpeedRate(String sourceData) {
        JSONObject convertMessage = JSONUtil.parseObj(sourceData);
        JSONObject data = JSONUtil.parseObj(convertMessage.get("data"));
        if (data == null) {
            log.warn("Data in received kafka message is null");
            return sourceData;
        }

        for (String speedFieldName : speedList) {
            Object sourceSpeed = data.get(speedFieldName);

            if (sourceSpeed instanceof BigDecimal speed) {
                data.set(speedFieldName, speed.multiply(new BigDecimal(speedRate))
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        convertMessage.set("data", data);
        return JSONUtil.toJsonStr(convertMessage);
    }

    private void submitSendQueue(String topicName, List<String> messageStrList) {
        Queue<String> taskQueue = queueMap.get(topicName);
        // addALL也是CAS操作，入队是原子的
        taskQueue.addAll(messageStrList);

        // poll可以保证并发安全，顺序出队
        String messageStr;
        while ((messageStr = taskQueue.poll()) != null) {
            sendMessageToAdmin(MessageType.TASK_EVENT_RECEIVE.getType(), messageStr);
        }
    }

    private void submitSendQueue(String topicName, String message, Object token) {
        // 单个消息提交进队列，会被正在全量发送消息的线程，一同发送；
        Queue<String> taskQueue = queueMap.get(topicName);
        taskQueue.add(message);

        // 如果当前队列只有一个任务，那么直接执行发送
        // 如果当前队列不止有一个任务，那么当前U3D webSocket重启了，正在发送全量任务，由它进行发送
        // 临界态 当queue.isEmpty，add一条数据，不会被全量任务执行，当前会补发
        // 竞争态 当前任务 与 全量任务size都为1执行时，poll线程安全保证顺序，所以需要判null
        if (taskQueue.size() != 1) {
            return;
        }

        // 在并发执行时，poll可以顺序出队，但无法保证出队的一定不为空，因为在另一个线程可能已经出队执行
        String messageStr = taskQueue.poll();
        if (messageStr != null && isInvalidToken(token)) {
            sendMessageToAdmin(MessageType.TASK_EVENT_RECEIVE.getType(), messageStr);
        }
    }

    private void updateRedisStatus(String topicName, String dataHashKey, RedisDeviceInfoDTO redisDeviceInfo) {
        redisDeviceInfo.setStatus(BusinessRedisConstants.SEND);
        stringRedisTemplate.opsForHash().put(topicName, dataHashKey, JSONUtil.toJsonStr(redisDeviceInfo));
    }

    private void sendMessageToAdmin(String messageType, String content) {
        try {
            webSocketMessageSender.send(messageType, content);
            log.info("********************原始消息处理后，转发给websocket: {}", content);
        } catch (Exception e) {
            log.error("Error sending message to admin, messageType: {}, content: {}", messageType, content, e);
        }
    }

    /**
     * 当前队列是否可以发送消息
     * 1、session连接未建立，自然不可发送
     * @return 是否可以发送
     */
    boolean isNotCanSend() {
        if (webSocketMessageSender.isOpen()) {
            return false;
        }
        log.info("当前webSocket没有连接，无需发送");
        return true;
    }

    public void lockTopic(String lockName) {
        lockMap.get(lockName).lock();
    }

    public void unlockTopic(String lockName) {
        lockMap.get(lockName).unlock();
    }
}
