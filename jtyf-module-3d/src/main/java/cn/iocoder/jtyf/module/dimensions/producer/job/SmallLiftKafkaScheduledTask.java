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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author akon
 * @date 2025-05-16
 * @desc 小型吊装设备动态模拟类
 * 该类用于模拟小型吊装设备的运行状态，包括回转、变幅、提升等参数的信息发送。
 * 该类的实现类需要在 Spring 容器中被管理，以便在需要时启动和停止定时任务。
 */
@Slf4j
@Component
public class SmallLiftKafkaScheduledTask implements IKafkaScheduledTask {

    // 设备基本信息
    private static final String DEVICE_CODE = "XD025-XD-1";
    private static final String DEVICE_NAME = "小型吊装设备XD01";
    private static final String DEVICE_TYPE = "small_lift";
    private static final String FIXED_COMMAND = "start_small_hoisting"; // 固定命令

    // 模拟参数配置
    private final int TIME_INTERVAL_MS = 500; // 数据更新频率
    private static final double PARAMETER_CHANGE_STEP = 0.01; // 参数变化步长
    private static final double SPEED_MAX = 0.1; // 最大速度

    // 设备状态
    private double rotationParameters_speed = 0.01;
    private double rotationParameters_location = 0.01;
    private double swingParameters_speed = 0.01;
    private double swingParameters_location = 0.01;
    private double liftingParameters_speed = 0.01;
    private double liftingParameters_location = 0.01;
    private double hookPoint1_speed = 0.01;
    private double hookPoint1_location = 0.01;
    private double hookPoint2_speed = 0.01;
    private double hookPoint2_location = 0.01;
    private String currentActionState = "1";
    private int status = 1; // 1: 正常，0: 故障
    private String faultAlarmInformation = "无故障";
    private int current = 60;
    private int voltage = 220;
    private final String command = FIXED_COMMAND; // 固定命令，不可变

    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Resource
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;

    @Override
    public void startTask() {
        if (future == null || future.isCancelled()) {
            future = taskScheduler.scheduleAtFixedRate(
                    this::sendMessageToKafka,
                    Instant.now(),
                    Duration.ofMillis(TIME_INTERVAL_MS)
            );
            log.info("SmallLiftEquipmentSimulator started with command: {}", command);
        }
    }

    @Override
    public void stopTask() {
        if (future != null && !future.isCancelled()) {
            log.info("Attempting to cancel the SmallLiftEquipmentSimulator task...");
            boolean cancelled = future.cancel(true);
            log.info("Task cancellation result: {}", cancelled);
        }
    }

    @Override
    public void sendMessage(GeneralReceiveMessage message) {
        // 不实现
    }

    /**
     * 处理固定命令逻辑（忽略外部指令）
     */
    private void handleCommand() {
        // 固定为小型吊装设备起吊操作
        liftingParameters_speed = 0.05; // 设置提升速度
        rotationParameters_speed = 0.02; // 设置回转速度
        swingParameters_speed = 0.03; // 设置变幅速度
        log.debug("Using fixed command: {}", command);
    }

    public void sendMessageToKafka() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        handleCommand(); // 应用固定命令逻辑
        updateEquipmentStatus(); // 更新设备状态

        Map<String, Object> message = createMessage();
        KafkaWebSocketMessage kafkaWebSocketMessage = JsonUtils.parseObject(JsonUtils.toJsonString(message), KafkaWebSocketMessage.class);
        sendKafkaMessage(kafkaWebSocketMessage);
        log.debug("Small Lift Equipment Message sent to Kafka: {}", kafkaWebSocketMessage);
    }

    private Map<String, Object> createMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("version", "v2");
        message.put("type", "request");

        Map<String, Object> data = new HashMap<>();
        data.put("deviceCode", DEVICE_CODE);
        data.put("deviceName", DEVICE_NAME);
        data.put("deviceType", DEVICE_TYPE);
        data.put("rotationParameters_speed", rotationParameters_speed);
        data.put("rotationParameters_location", rotationParameters_location);
        data.put("swingParameters_speed", swingParameters_speed);
        data.put("swingParameters_location", swingParameters_location);
        data.put("liftingParameters_speed", liftingParameters_speed);
        data.put("liftingParameters_location", liftingParameters_location);
        data.put("hookPoint1_speed", hookPoint1_speed);
        data.put("hookPoint1_location", hookPoint1_location);
        data.put("hookPoint2_speed", hookPoint2_speed);
        data.put("hookPoint2_location", hookPoint2_location);
        data.put("currentActionState", currentActionState);
        data.put("status", status);
        data.put("faultAlarmInformation", faultAlarmInformation);
        data.put("current", current);
        data.put("voltage", voltage);
        data.put("command", command); // 固定命令

        message.put("data", data);

        return message;
    }

    private void updateEquipmentStatus() {
        // 如果设备处于故障状态，仅更新部分状态
        if (status == 0) {
            // 故障状态下随机波动电流和电压
            current = 60 + ThreadLocalRandom.current().nextInt(-10, 10);
            voltage = 220 + ThreadLocalRandom.current().nextInt(-5, 5);
            return;
        }

        // 更新参数
        rotationParameters_location += rotationParameters_speed * (TIME_INTERVAL_MS / 1000.0);
        swingParameters_location += swingParameters_speed * (TIME_INTERVAL_MS / 1000.0);
        liftingParameters_location += liftingParameters_speed * (TIME_INTERVAL_MS / 1000.0);
        hookPoint1_location += hookPoint1_speed * (TIME_INTERVAL_MS / 1000.0);
        hookPoint2_location += hookPoint2_speed * (TIME_INTERVAL_MS / 1000.0);

        // 边界检查（示例：参数限制在0-360度范围内）
        rotationParameters_location = rotationParameters_location % 360;
        swingParameters_location = swingParameters_location % 360;
        liftingParameters_location = Math.max(0, Math.min(100, liftingParameters_location));
        hookPoint1_location = Math.max(0, Math.min(100, hookPoint1_location));
        hookPoint2_location = Math.max(0, Math.min(100, hookPoint2_location));

        // 随机波动电流和电压
        current = 60 + ThreadLocalRandom.current().nextInt(-5, 5);
        voltage = 220 + ThreadLocalRandom.current().nextInt(-2, 2);

        // 随机模拟故障（降低概率至1%）
        if (ThreadLocalRandom.current().nextDouble() < 0.01) {
            simulateFault();
        }
    }

    private void simulateFault() {
        status = 0;
        faultAlarmInformation = "模拟故障：电机过热";
        log.warn("设备进入故障状态: {}", faultAlarmInformation);
    }

    private void sendKafkaMessage(KafkaWebSocketMessage kafkaWebSocketMessage) {
        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC, kafkaWebSocketMessage);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Message sent successfully to Kafka: {}", result.getRecordMetadata());
            } else {
                log.error("[sendKafkaMessage][发送消息({}) 到 Kafka 失败]", kafkaWebSocketMessage, ex);
            }
        });
    }
}