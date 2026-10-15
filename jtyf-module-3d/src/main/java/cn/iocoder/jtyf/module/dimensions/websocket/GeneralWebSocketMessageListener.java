package cn.iocoder.jtyf.module.dimensions.websocket;

import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.websocket.core.listener.WebSocketMessageListener;
import cn.iocoder.jtyf.module.dimensions.consumer.common.KafkaTopicsConstants;
import cn.iocoder.jtyf.module.dimensions.producer.*;
import cn.iocoder.jtyf.module.dimensions.websocket.enums.MessageType;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralReceiveMessage;
import cn.iocoder.jtyf.module.dimensions.websocket.message.GeneralSendMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * @author akon
 * @desc WebSocket 通用通道
 * @create 2025-05-07
 * <p>
 * @example 消息结构参考：
 * 地址： ws://127.0.0.1:48080/dimensions/ws
 * <p>
 * {
 * "type": "general-message-send",
 * "content": "{\"businessModule\":\"ELECTRIC_CENTER_TOPIC\",\"text\":{\"name\":\"张三\",\"sex\":\"男\",\"age\":\"30\"}}"
 * }
 * <p>
 * {
 * "type": "general-message-send",
 * "content": "{\"businessModule\":\"BUILDING_CENTER_TOPIC\",\"text\":{\"name\":\"张三\",\"sex\":\"男\",\"age\":\"30\"}}"
 * }
 * <p>
 * 说明：
 * type不可变，代表通道；
 * content的值必须是一个json字符串
 */
@Slf4j
@Component
public class GeneralWebSocketMessageListener implements WebSocketMessageListener<GeneralSendMessage> {

    @Resource
    private BuildingKafkaScheduledTask buildingTask;

    @Resource
    private TransportationKafkaScheduledTask transportationTask;

    @Resource
    private VentilationKafkaScheduledTask ventilationTask;

    @Resource
    private ElectricKafkaScheduledTask electricTask;

    @Resource
    private WaterSupDraKafkaScheduledTask waterSupDraTask;

    @Resource
    private EnvironmentalProKafkaScheduledTask environmentalProTask;

    @Resource
    private FireKafkaScheduledTask fireTask;

    @Resource
    private PersonnelKafkaScheduledTask personnelTask;

    @Resource
    private SecurityKafkaScheduledTask securityTask;

    @Resource
    private CameraKafkaScheduledTask cameraTask;

    private final Map<String, KafkaTask> taskMap = new HashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        taskMap.put(KafkaTopicsConstants.BUILDING_CENTER_TOPIC, buildingTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.TRANSPORTATION_CENTER_TOPIC, transportationTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.VENTILATION_CENTER_TOPIC, ventilationTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.ELECTRIC_CENTER_TOPIC, electricTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.WATER_SUPPLY_AND_DRAINAGE_CENTER_TOPIC, waterSupDraTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.ENVIRONMENTAL_PROTECTION_CENTER_TOPIC, environmentalProTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.FIRE_CENTER_TOPIC, fireTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.PERSONNEL_CENTER_TOPIC, personnelTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.SECURITY_CENTER_TOPIC, securityTask::sendMessage);
        taskMap.put(KafkaTopicsConstants.CAMERA_CENTER_TOPIC, cameraTask::sendMessage);
    }

    @FunctionalInterface
    interface KafkaTask {
        void send(GeneralReceiveMessage message);
    }

    @Override
    public void onMessage(WebSocketSession session, GeneralSendMessage message) {
        GeneralReceiveMessage receiveMessage;
        try {
            receiveMessage = new GeneralReceiveMessage().setText(JsonUtils.toJsonString(message.getText()));
            sendMessageToTask(receiveMessage, message.getBusinessModule());
        } catch (Exception e) {
            log.error("将消息转换为 JSON 字符串时出错，原始消息: {}", message, e);
        }
    }

    @Override
    public String getType() {
        return MessageType.GENERAL_MESSAGE_SEND.getType();
    }

    private void sendMessageToTask(GeneralReceiveMessage receiveMessage, String businessModule) {
        log.info("准备将消息发送到主题: {}", businessModule);

        KafkaTask task = taskMap.get(businessModule);
        if (task != null) {
            try {
                receiveMessage.setTopic(businessModule);
                task.send(receiveMessage);
                log.info("消息已成功发送到业务模块: {}, 主题: {}", task, businessModule);
            } catch (Exception e) {
                log.error("发送消息到业务模块: {}, 主题: {} 时出错", task, businessModule, e);
            }
        } else {
            log.warn("未知的任务类型: {}", businessModule);
        }
    }
}