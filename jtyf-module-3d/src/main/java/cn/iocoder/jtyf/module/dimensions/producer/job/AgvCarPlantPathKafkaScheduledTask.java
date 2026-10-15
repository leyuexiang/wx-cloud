package cn.iocoder.jtyf.module.dimensions.producer.job;

import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.producer.IKafkaScheduledTask;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/**
 * @author akon
 * @create 2025-04-25
 * @desc 收集AGV小车回字形路径点位信息的定时任务
 * 该类用于提前收集AgvCarKafkaScheduledTask中生成的回字形路径上所有的currentPosition信息，
 * 并将这些信息封装成消息发送到Kafka主题。
 */
@Slf4j
@Component
public class AgvCarPlantPathKafkaScheduledTask implements IKafkaScheduledTask {

    private static final int SEND_INTERVAL_MS = 5000; // 几秒发送一次，这里设置为5秒

    @Resource
    private AgvCarKafkaScheduledTask agvCarKafkaScheduledTask;

    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Resource
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;

    // 存储回字形路径的所有currentPosition信息
    private final List<String> plantPath = new ArrayList<>();

    @Override
    public void startTask() {
        if (future == null || future.isCancelled()) {
            // 使用新的 scheduleAtFixedRate 方法，结合 Duration 类型
            future = taskScheduler.scheduleAtFixedRate(
                    this::sendMessageToKafka,
                    Instant.now(),
                    Duration.ofMillis(SEND_INTERVAL_MS)
            );
            log.info("AgvCarPlantPathKafkaScheduledTask started.");
        }
    }

    @Override
    public void stopTask() {
        if (future != null && !future.isCancelled()) {
            log.info("Attempting to cancel the AgvCarPlantPathKafkaScheduledTask task...");
            boolean cancelled = future.cancel(true);
            log.info("Task cancellation result: {}", cancelled);
        }
    }

    @Override
    public void sendMessage(GeneralReceiveMessage receiveMessage) {
        // 不实现
    }

    public void sendMessageToKafka() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        // 提前收集所有点位信息
        collectAllPositions();
        // 发送包含路径信息的消息
        Map<String, Object> message = createMessage();
        KafkaWebSocketMessage kafkaWebSocketMessage = JsonUtils.parseObject(JsonUtils.toJsonString(message), KafkaWebSocketMessage.class);
        sendKafkaMessage(kafkaWebSocketMessage);
        log.info("AGV Car Plant Path Message sent to Kafka: {}", kafkaWebSocketMessage);
    }

    private void collectAllPositions() {
        // 重置AgvCarKafkaScheduledTask的状态
        agvCarKafkaScheduledTask.reset();

        // 后续代码保持不变
        double currentX = 0;
        double currentZ = 0;
        int phase = 0;
        double accumulatedX = 0;
        double accumulatedZ = 0;
        boolean isLoaded = agvCarKafkaScheduledTask.isLoaded();
        int TIME_INTERVAL_MS = agvCarKafkaScheduledTask.getTIME_INTERVAL_MS();
        double PHASE_CHANGE_THRESHOLD = AgvCarKafkaScheduledTask.getPHASE_CHANGE_THRESHOLD();

        plantPath.clear();

        // 给while循环添加标签
        outerLoop: while (true) {
            String currentPosition = String.format("%.3f,0,%.3f", currentX, currentZ);
            plantPath.add(currentPosition);

            // 根据速度和时间间隔计算步长
            int speed = isLoaded? AgvCarKafkaScheduledTask.getLOADED_SPEED() : AgvCarKafkaScheduledTask.getUNLOADED_SPEED();
            // 转换为 m/200ms
            double step = speed * (TIME_INTERVAL_MS / 60000.0);
            // 确保步长至少为1
            int moveStep = Math.max(1, (int) Math.min(step, AgvCarKafkaScheduledTask.getSMALL_STEP()));

            switch (phase) {
                // 向右移动
                case 0:
                    currentX += moveStep;
                    accumulatedX += moveStep;
                    if (accumulatedX >= PHASE_CHANGE_THRESHOLD) {
                        phase = 1;
                        accumulatedX = 0;
                    }
                    break;
                // 向下移动
                case 1:
                    currentZ -= moveStep;
                    accumulatedZ += moveStep;
                    if (accumulatedZ >= PHASE_CHANGE_THRESHOLD) {
                        phase = 2;
                        accumulatedZ = 0;
                    }
                    break;
                // 向左移动
                case 2:
                    currentX -= moveStep;
                    accumulatedX += moveStep;
                    if (accumulatedX >= PHASE_CHANGE_THRESHOLD) {
                        phase = 3;
                        accumulatedX = 0;
                    }
                    break;
                // 向上移动
                case 3:
                    currentZ += moveStep;
                    accumulatedZ += moveStep;
                    if (accumulatedZ >= PHASE_CHANGE_THRESHOLD) {
                        phase = 0;
                        accumulatedZ = 0;
                        // 完成一个完整的回字形路径，使用带标签的break跳出外层循环
                        if (currentX == 0 && currentZ == 0) {
                            break outerLoop;
                        }
                    }
                    break;
            }
        }
    }

    private Map<String, Object> createMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("version", "v1");
        message.put("type", "request");

        Map<String, Object> businessData = new HashMap<>();
        businessData.put("deviceId", "102");
        businessData.put("deviceName", "AGV路径收集器");
        businessData.put("deviceCode", "AGVPATHCOLLECTOR01-1");
        businessData.put("deviceType", "agv_car_plant");
        List<List<String>> pathList = new ArrayList<>();
        pathList.add(plantPath);
        pathList.add(plantPath); // 模拟两条相同的路径
        businessData.put("plantPath", pathList);

        message.put("data", businessData);

        return message;
    }

    private void sendKafkaMessage(KafkaWebSocketMessage kafkaWebSocketMessage) {
        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC, kafkaWebSocketMessage);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Message sent successfully to Kafka: {}", result.getRecordMetadata());
            } else {
                log.error("[sendKafkaMessage][发送消息({}) 到 Kafka 失败]", kafkaWebSocketMessage, ex);
            }
        });
    }
}