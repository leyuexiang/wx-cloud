package cn.iocoder.jtyf.module.dimensions.producer.job;

import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.websocket.core.sender.kafka.KafkaWebSocketMessage;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.producer.IKafkaScheduledTask;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import jakarta.annotation.Resource;
import lombok.Getter;
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

/**
 * @author akon
 * @create 2025-04-25
 * @desc 小车定时任务
 * 该类用于实现小车的定时任务，包括小车的位置、速度、方向等信息的发送。
 * 该类的实现类需要在 Spring 容器中被管理，以便在需要时启动和停止定时任务。
 */
@Slf4j
@Component
public class AgvCarKafkaScheduledTask implements IKafkaScheduledTask {

    // 满载移动速度，单位：m/min
    @Getter
    private static final int LOADED_SPEED = 30;
    // 空载移动速度，单位：m/min
    @Getter
    private static final int UNLOADED_SPEED = 50;
    // 每次小步移动的距离，根据速度和时间间隔调整
    @Getter
    private static final int SMALL_STEP = 1;
    // 回字形轨迹的阶段，0: 右，1: 下，2: 左，3: 上
    private int phase = 0;
    private double currentX = 0;
    private double currentZ = 0;
    // 新增变量用于累计移动距离
    private double accumulatedX = 0;
    private double accumulatedZ = 0;
    // 小车是否满载
    @Getter
    private final boolean isLoaded = false;

    @Getter
    private final int TIME_INTERVAL_MS = 200;

    private static final String DEVICE_CODE = "AGVGRZ01-1";
    private static final String COMMAND = "to_storage_area";
    @Getter
    private static final double PHASE_CHANGE_THRESHOLD = 60;

    // 新增设备信息常量
    private static final String DEVICE_ID = "101";
    private static final String DEVICE_NAME = "AGV运载器";
    private static final String DEVICE_TYPE = "agv_car";

    // 电池状态信息
    private int batteryVoltage = 360;
    private int batteryCurrent = 15;
    private String batteryHealth = "良好";
    private String batteryStatus = "未连接";
    private int batteryLevel = 87;

    // 其他设备状态信息
    private String currentTag = "";
    private String obstacleInfo = "无障碍";
    private String faultLevel3 = "正常";
    private String runMode = "to_storage_area";
    private String state = "移动中";

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
                    Duration.ofMillis(TIME_INTERVAL_MS)
            );
            log.info("AgvCarKafkaScheduledTask started.");
        }
    }

    @Override
    public void stopTask() {
        if (future != null && !future.isCancelled()) {
            log.info("Attempting to cancel the scheduled task...");
            boolean cancelled = future.cancel(true);
            log.info("Task cancellation result: {}", cancelled);
        }
    }

    @Override
    public void sendMessage(GeneralReceiveMessage receiveMessage) {
        //不实现
    }

    public void sendMessageToKafka() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }
        Map<String, Object> message = createMessage();
        KafkaWebSocketMessage kafkaWebSocketMessage = JsonUtils.parseObject(JsonUtils.toJsonString(message), KafkaWebSocketMessage.class);
        sendKafkaMessage(kafkaWebSocketMessage);
        log.info("Agv Car Message sent to Kafka: {}", kafkaWebSocketMessage);
    }

    private Map<String, Object> createMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("version", "v1");
        message.put("type", "request");

        Map<String, Object> businessData = new HashMap<>();
        businessData.put("deviceId", DEVICE_ID);
        businessData.put("deviceName", DEVICE_NAME);
        businessData.put("deviceCode", DEVICE_CODE);
        businessData.put("deviceType", DEVICE_TYPE);
        businessData.put("management", ""); // 修正拼写错误
        businessData.put("batteryVoltage", batteryVoltage); // 修正拼写错误
        businessData.put("batteryCurrent", batteryCurrent);
        businessData.put("batteryHealth", batteryHealth);
        businessData.put("batteryStatus", batteryStatus);
        businessData.put("batteryLevel", batteryLevel);
        businessData.put("currentTag", currentTag);
        businessData.put("obstacleInfo", obstacleInfo); // 修正拼写错误

        // 计算当前速度
        int speed = isLoaded? LOADED_SPEED : UNLOADED_SPEED;
        businessData.put("speed", speed);

        businessData.put("faultLevel3", faultLevel3);

        // 当前方向，根据移动阶段动态计算
        double currentHeading = calculateCurrentHeading();
        businessData.put("currentHeading", currentHeading);

        // 小车升降高度
        double liftHeight = 0;
        businessData.put("liftHeight", liftHeight);

        String currentPosition = String.format("%.3f,0,%.3f", currentX, currentZ);
        currentPosition = modifyCurrentPosition(currentPosition);
        String[] parts = currentPosition.split(",");
        currentX = Double.parseDouble(parts[0]);
        currentZ = Double.parseDouble(parts[2]);
        businessData.put("currentPosition", currentPosition);

        businessData.put("command", COMMAND);
        businessData.put("runMode", runMode);
        businessData.put("state", state);

        message.put("data", businessData);

        return message;
    }

    // 根据移动阶段计算当前方向
    private double calculateCurrentHeading() {
        return switch (phase) {
            case 0 -> // 右
                    0.0;
            case 1 -> // 下
                    270.0;
            case 2 -> // 左
                    180.0;
            case 3 -> // 上
                    90.0;
            default -> 0.0;
        };
    }

    // 模拟修改currentPosition的方法，实现回字形轨迹
    private String modifyCurrentPosition(String currentPosition) {
        String[] parts = currentPosition.split(",");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        double z = Double.parseDouble(parts[2]);

        // 根据速度和时间间隔计算步长
        int speed = isLoaded? LOADED_SPEED : UNLOADED_SPEED;
        // 转换为 m/200ms
        double step = speed * (TIME_INTERVAL_MS / 60000.0);
        // 确保步长至少为1
        int moveStep = Math.max(1, (int) Math.min(step, SMALL_STEP));

        switch (phase) {
            // 向右移动
            case 0:
                x += moveStep;
                accumulatedX += moveStep;
                updatePositionAndPhase(x, accumulatedX, 1);
                break;
            // 向下移动
            case 1:
                z -= moveStep;
                accumulatedZ += moveStep;
                updatePositionAndPhase(z, accumulatedZ, 2);
                break;
            // 向左移动
            case 2:
                x -= moveStep;
                accumulatedX += moveStep;
                updatePositionAndPhase(x, accumulatedX, 3);
                break;
            // 向上移动
            case 3:
                z += moveStep;
                accumulatedZ += moveStep;
                updatePositionAndPhase(z, accumulatedZ, 0);
                break;
        }

        return String.format("%.3f,%.3f,%.3f", x, y, z);
    }

    private void updatePositionAndPhase(double currentValue, double accumulatedValue, int nextPhase) {
        if (accumulatedValue >= PHASE_CHANGE_THRESHOLD) {
            if (phase == 0 || phase == 2) {
                currentX = currentValue;
            } else {
                currentZ = currentValue;
            }
            phase = nextPhase;
            if (phase == 0 || phase == 2) {
                accumulatedX = 0;
            } else {
                accumulatedZ = 0;
            }
            log.info("Switching phase to {} ({} movement)", phase, phase == 0? "rightward" : phase == 1? "downward" : phase == 2? "leftward" : "upward");
        }
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

    public void reset() {
        this.phase = 0;
        this.currentX = 0;
        this.currentZ = 0;
        this.accumulatedX = 0;
        this.accumulatedZ = 0;
    }
}