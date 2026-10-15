package cn.iocoder.jtyf.module.dimensions.producer.job;

import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.producer.IKafkaScheduledTask;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;

/**
 * @author akon
 * @create 2025-04-25
 * @desc 门定时任务
 * 该类用于实现门的定时任务，包括门的位置、角度等信息的发送。
 * 该类的实现类需要在 Spring 容器中被管理，以便在需要时启动和停止定时任务。
 */
@Slf4j
@Component
public class DoorKafkaScheduledTask implements IKafkaScheduledTask {

    private static final class Constants {
        // 消息发送间隔，单位：毫秒
        private static final int MESSAGE_SEND_INTERVAL = 5000;
        // 角度增量范围
        private static final int ANGLE_INCREMENT_RANGE = 20;
        // 角度最大值
        private static final int ANGLE_MAX_VALUE = 360;
        // 轴的数量
        private static final int AXIS_COUNT = 3;
    }

    private static final Random random = new Random();
    private final int[] angles = {0, 0, 0};
    private int currentIndex = 0;
    private static final String[] AXES = {"x", "y", "z"};

    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Resource
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;

    @Override
    public void startTask() {
        if (future == null || future.isCancelled()) {
            // 使用新的 scheduleAtFixedRate 方法，结合 Duration 类型
            future = taskScheduler.scheduleAtFixedRate(
                    this::sendMessageToKafka,
                    Instant.now(),
                    Duration.ofMillis(Constants.MESSAGE_SEND_INTERVAL)
            );
            log.info("DoorKafkaScheduledTask started.");
        }
    }

    @Override
    public void stopTask() {
        if (future != null && !future.isCancelled()) {
            future.cancel(true);
            log.info("DoorKafkaScheduledTask stopped.");
        }
    }

    @Override
    public void sendMessage(GeneralReceiveMessage receiveMessage) {
        //不实现
    }

    public void sendMessageToKafka() {
        Map<String, Object> message = buildMessage();
        KafkaWebSocketMessage kafkaWebSocketMessage = JsonUtils.parseObject(JsonUtils.toJsonString(message), KafkaWebSocketMessage.class);
        try {
            kafkaTemplate.send(KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC, kafkaWebSocketMessage).get();
            log.info("Door Message sent to Kafka: {}", kafkaWebSocketMessage);
        } catch (InterruptedException | ExecutionException e) {
            log.error("[sendKafkaMessage][发送消息({}) 到 Kafka 失败]", kafkaWebSocketMessage, e);
        }
    }

    private Map<String, Object> buildMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("version", "v1");
        message.put("type", "request");

        Map<String, Object> businessData = new HashMap<>();
        businessData.put("deviceCode", "door1");
        businessData.put("deviceType", "agv_door");
        businessData.put("command", "open");

        int increment = random.nextInt(Constants.ANGLE_INCREMENT_RANGE) + 1;
        angles[currentIndex] += increment;

        // 处理角度超出最大值的情况
        angles[currentIndex] %= Constants.ANGLE_MAX_VALUE;

        businessData.put("axis", AXES[currentIndex]);
        businessData.put("angle", angles[currentIndex]);

        businessData.put("space", "0");
        businessData.put("speed", 5);

        Map<String, Object> data = new HashMap<>();
        data.put("businessData", businessData);
        data.put("deviceCode", "door1");
        data.put("command", "open");

        message.put("data", data);

        // 更新当前轴的索引
        currentIndex = (currentIndex + 1) % Constants.AXIS_COUNT;

        return message;
    }
}