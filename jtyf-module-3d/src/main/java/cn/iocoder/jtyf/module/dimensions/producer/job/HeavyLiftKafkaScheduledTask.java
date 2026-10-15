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
 * @desc 重载吊装设备动态模拟类
 * 该类用于模拟重载吊装设备的运行状态，包括位置、速度、各种状态指标等信息的发送。
 * 该类的实现类需要在 Spring 容器中被管理，以便在需要时启动和停止定时任务。
 */
@Slf4j
@Component
public class HeavyLiftKafkaScheduledTask implements IKafkaScheduledTask {

    // 设备基本信息
    private static final String DEVICE_CODE = "ZD-ZD32-1";
    private static final String DEVICE_NAME = "重载吊装设备ZD01";
    private static final String DEVICE_TYPE = "heavy_lift";
    private static final String FIXED_COMMAND = "start_hoisting"; // 固定命令

    // 模拟参数配置
    private static final int TIME_INTERVAL_MS = 500; // 数据更新频率
    private static final double MAIN_CAR_SPEED = 0.5; // 大车固定速度 (比小车慢)
    private static final double SUB_CAR_SPEED = 1.0;  // 小车固定速度
    private static final double HOOK_SPEED = 0.8;    // 吊钩固定速度

    // 设备状态
    private double mainCarPosition = 0.0;
    private double subCarPosition = 0.0;
    private double hookPosition = 0.0;
    private double mainCarSpeed = MAIN_CAR_SPEED;
    private double subCarSpeed = SUB_CAR_SPEED;
    private double liftingLoweringSpeed = HOOK_SPEED;
    private int brakingStatus = 0; // 0: 松开，1: 制动
    private int limitSwitchStatus = 0; // 0: 正常，1: 触发
    private int status = 1; // 1: 正常，0: 故障
    private String faultAlarmInformation = "无故障";
    private int automaticTransfer = 0; // 0: 自动，1: 手动
    private int current = 60;
    private int voltage = 220;
    private final String command = FIXED_COMMAND; // 固定命令，不可变

    // 运行模式和方向控制
    private OperationMode operationMode = OperationMode.NORMAL;
    private Direction mainCarDirection = Direction.FORWARD;
    private Direction subCarDirection = Direction.FORWARD;
    private Direction hookDirection = Direction.UP;

    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Resource
    private TaskScheduler taskScheduler;

    private ScheduledFuture<?> future;

    // 设备运行模式枚举
    private enum OperationMode {
        NORMAL, // 正常运行
        TEST,   // 测试模式
        FAULT   // 故障模式
    }

    // 方向控制枚举
    private enum Direction {
        FORWARD, // 前进/上升
        BACKWARD, // 后退/下降
        STOP, // 停止
        UP, // 吊钩上升
        DOWN // 吊钩下降
    }

    @Override
    public void startTask() {
        if (future == null || future.isCancelled()) {
            future = taskScheduler.scheduleAtFixedRate(
                    this::sendMessageToKafka,
                    Instant.now(),
                    Duration.ofMillis(TIME_INTERVAL_MS)
            );
            log.info("HeavyLiftEquipmentSimulator started with command: {}", command);
        }
    }

    @Override
    public void stopTask() {
        if (future != null && !future.isCancelled()) {
            log.info("Attempting to cancel the HeavyLiftEquipmentSimulator task...");
            boolean cancelled = future.cancel(true);
            log.info("Task cancellation result: {}", cancelled);
        }
    }

    @Override
    public void sendMessage(GeneralReceiveMessage message) {
        // 不实现
    }

    /**
     * 处理接收到的指令（固定为start_hoisting，忽略外部指令）
     */
    private void handleCommand() {
        // 固定设置为起吊方向和自动模式
        hookDirection = Direction.UP;
        brakingStatus = 0; // 松开制动
        automaticTransfer = 0; // 自动模式
        log.debug("Using fixed command: {}", command);
    }

    /**
     * 模拟设备故障
     */
    private void simulateFault() {
        status = 0;
        faultAlarmInformation = "模拟故障：电机过热";
        log.warn("设备进入故障状态: {}", faultAlarmInformation);
    }

    /**
     * 清除设备故障，恢复正常运行
     */
    private void clearFault() {
        status = 1;
        faultAlarmInformation = "无故障";
        log.info("设备故障已清除，恢复正常运行");
    }

    /**
     * 定时发送消息到 Kafka
     */
    public void sendMessageToKafka() {
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        handleCommand(); // 应用固定命令逻辑
        updateEquipmentStatus(); // 更新设备状态

        Map<String, Object> message = createMessage();
        KafkaWebSocketMessage kafkaWebSocketMessage = JsonUtils.parseObject(JsonUtils.toJsonString(message), KafkaWebSocketMessage.class);
        sendKafkaMessage(kafkaWebSocketMessage);
        log.debug("Heavy Lift Equipment Message sent to Kafka: {}", kafkaWebSocketMessage);
    }

    /**
     * 创建要发送的消息
     * @return 包含设备信息和状态的消息
     */
    private Map<String, Object> createMessage() {
        Map<String, Object> message = new HashMap<>();
        message.put("version", "v2");
        message.put("type", "request");

        Map<String, Object> data = new HashMap<>();
        data.put("deviceCode", DEVICE_CODE);
        data.put("deviceName", DEVICE_NAME);
        data.put("deviceType", DEVICE_TYPE);
        data.put("mainCarPosition", mainCarPosition);
        data.put("subCarPosition", subCarPosition);
        data.put("hookPosition", hookPosition);
        data.put("mainCarSpeed", mainCarSpeed);
        data.put("subCarSpeed", subCarSpeed);
        data.put("liftingLoweringSpeed", liftingLoweringSpeed);
        data.put("brakingStatus", brakingStatus);
        data.put("limitSwitchStatus", limitSwitchStatus);
        data.put("status", status);
        data.put("faultAlarmInformation", faultAlarmInformation);
        data.put("automaticTransfer", automaticTransfer);
        data.put("current", current);
        data.put("voltage", voltage);
        data.put("command", command); // 固定命令

        message.put("data", data);
        return message;
    }

    /**
     * 更新设备状态
     */
    private void updateEquipmentStatus() {
        if (status == 0) {
            // 故障状态处理
            current = 60 + ThreadLocalRandom.current().nextInt(-10, 10);
            voltage = 220 + ThreadLocalRandom.current().nextInt(-5, 5);
            return;
        }

        // 正常状态更新位置和速度
        if (brakingStatus == 0) {
            // 更新大车位置 (0-100范围内递增或递减)
            updatePositionWithDirection(
                    mainCarPosition,
                    mainCarSpeed,
                    mainCarDirection,
                    0.0,
                    100.0,
                    position -> mainCarPosition = position,
                    direction -> mainCarDirection = direction
            );

            // 更新小车位置 (0-100范围内递增或递减)
            updatePositionWithDirection(
                    subCarPosition,
                    subCarSpeed,
                    subCarDirection,
                    0.0,
                    100.0,
                    position -> subCarPosition = position,
                    direction -> subCarDirection = direction
            );

            // 更新吊钩位置 (0-100范围内递增或递减)
            updatePositionWithDirection(
                    hookPosition,
                    liftingLoweringSpeed,
                    hookDirection,
                    0.0,
                    100.0,
                    position -> hookPosition = position,
                    direction -> hookDirection = direction
            );
        } else {
            // 制动状态下速度设为0
            mainCarSpeed = 0;
            subCarSpeed = 0;
            liftingLoweringSpeed = 0;
        }

        // 随机波动电流电压
        current = 60 + ThreadLocalRandom.current().nextInt(-5, 5);
        voltage = 220 + ThreadLocalRandom.current().nextInt(-2, 2);

        // 随机触发限位开关（概率1%）
        limitSwitchStatus = ThreadLocalRandom.current().nextDouble() < 0.01 ? 1 : 0;
    }

    /**
     * 更新位置并处理边界条件
     */
    private void updatePositionWithDirection(
            double position,
            double speed,
            Direction direction,
            double minPos,
            double maxPos,
            java.util.function.Consumer<Double> positionSetter,
            java.util.function.Consumer<Direction> directionSetter
    ) {
        // 计算每次更新的距离 (考虑时间间隔)
        double distance = speed * (TIME_INTERVAL_MS / 1000.0);

        // 根据方向更新位置
        if (direction == Direction.FORWARD || direction == Direction.UP) {
            position += distance;
            // 到达上限时改变方向
            if (position >= maxPos) {
                position = maxPos;
                directionSetter.accept(direction == Direction.UP ? Direction.DOWN : Direction.BACKWARD);
            }
        } else if (direction == Direction.BACKWARD || direction == Direction.DOWN) {
            position -= distance;
            // 到达下限时改变方向
            if (position <= minPos) {
                position = minPos;
                directionSetter.accept(direction == Direction.DOWN ? Direction.UP : Direction.FORWARD);
            }
        }

        // 更新位置
        positionSetter.accept(position);
    }

    /**
     * 发送消息到 Kafka
     */
    private void sendKafkaMessage(KafkaWebSocketMessage kafkaWebSocketMessage) {
        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(
                KafkaTopicsConstants.COMMON_UNIVERSAL_TOPIC,
                kafkaWebSocketMessage
        );
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Message sent successfully: {}", result.getRecordMetadata());
            } else {
                log.error("Failed to send message: {}", kafkaWebSocketMessage, ex);
            }
        });
    }
}