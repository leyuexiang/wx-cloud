package cn.iocoder.wx.module.dimensions.websocket;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.common.util.json.JsonUtils;
import cn.iocoder.wx.framework.websocket.core.listener.WebSocketMessageListener;
import cn.iocoder.wx.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import cn.iocoder.wx.module.dimensions.producer.IKafkaScheduledTask;
import cn.iocoder.wx.module.dimensions.producer.job.*;
import cn.iocoder.wx.module.dimensions.websocket.enums.MessageType;
import cn.iocoder.wx.module.dimensions.websocket.enums.TaskAction;
import cn.iocoder.wx.module.dimensions.websocket.message.TaskEventReceiveMessage;
import cn.iocoder.wx.module.dimensions.websocket.message.TaskEventSendMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * @author akon
 * @desc WebSocket 测试、演示任务通道
 * 使用策略模式替代多重条件判断
 * @create 2025-05-07
 * <p>
 * @example 消息结构参考：
 * 地址： ws://127.0.0.1:48080/dimensions/ws
 * <p>
 * 车辆任务开启：
 * {
 * "type": "task-event-message-send",
 * "content": "{\"text\":{\"car\":\"start\"}}"
 * }
 * <p>
 * 车辆任务停止：
 * {
 * "type": "task-event-message-send",
 * "content": "{\"text\":{\"car\":\"stop\"}}"
 * }
 * <p>
 * <p>
 * 大门任务开启：
 * {
 * "type": "task-event-message-send",
 * "content": "{\"text\":{\"door\":\"start\"}}"
 * }
 * <p>
 * 大门任务停止：
 * {
 * "type": "task-event-message-send",
 * "content": "{\"text\":{\"door\":\"stop\"}}"
 * }
 * <p>
 * 说明：
 * type不可变，代表通道；
 * content的值必须是一个json字符串
 */
@Slf4j
@Component
public class TaskEventWebSocketMessageListener implements WebSocketMessageListener<TaskEventSendMessage> {

    @Resource
    private WebSocketMessageSender webSocketMessageSender;

    // 使用Map存储任务处理器，替代大量的switch-case
    private final Map<String, IKafkaScheduledTask> taskHandlerMap = new HashMap<>();

    // 通过构造函数注入所有任务处理器
    public TaskEventWebSocketMessageListener(
            DoorKafkaScheduledTask doorTask,
            AgvCarKafkaScheduledTask agvCarTask,
            HeavyLiftKafkaScheduledTask heavyLiftTask,
            SmallLiftKafkaScheduledTask smallLiftTask,
            AgvCarPlantPathKafkaScheduledTask agvCarPlantPathTask
    ) {

        // 注册任务处理器
        taskHandlerMap.put("agv_door", doorTask);
        taskHandlerMap.put("agv_car", agvCarTask);
        taskHandlerMap.put("heavy_lift", heavyLiftTask);
        taskHandlerMap.put("small_lift", smallLiftTask);
        taskHandlerMap.put("agv_car_plant", agvCarPlantPathTask);
    }

    @Override
    public void onMessage(WebSocketSession session, TaskEventSendMessage message) {
        Long fromUserId = WebSocketFrameworkUtils.getLoginUserId(session);
        TaskEventReceiveMessage toMessage = new TaskEventReceiveMessage()
                .setFromUserId(fromUserId)
                .setText(JsonUtils.toJsonString(message.getText()))
                .setSingle(true);

        // 情况一：单发, 给指定用户
        if (message.getToUserId() != null) {
            webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), message.getToUserId(),
                    MessageType.TASK_EVENT_RECEIVE.getType(), toMessage);
        } else {
            // 情况二：群发; 给所有用户
            toMessage.setSingle(false);
            webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(),
                    MessageType.TASK_EVENT_RECEIVE.getType(), toMessage);
        }
        processKafkaTasks(toMessage);
    }

    @Override
    public String getType() {
        return MessageType.TASK_EVENT_SEND.getType();
    }

    // 处理Kafka任务
    private void processKafkaTasks(TaskEventReceiveMessage toMessage) {
        log.info("处理Kafka任务: {}", toMessage);
        try {
            JSONObject messageObj = JSONUtil.parseObj(toMessage.getText());
            messageObj.forEach((taskType, action) -> {
                // 对每个任务单独进行异常处理，避免一个任务失败影响其他任务
                try {
                    handleTask(taskType, action.toString());
                } catch (Exception e) {
                    log.error("处理任务 {} 时发生错误: {}", taskType, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("解析消息内容出错，消息内容: {}", toMessage.getText(), e);
        }
    }

    // 处理单个任务
    private void handleTask(String taskType, String action) {
        IKafkaScheduledTask task = taskHandlerMap.get(taskType);
        if (task == null) {
            log.warn("未知的任务类型: {}", taskType);
            return;
        }

        // 使用对应的action，执行相应的任务
        try {
            TaskAction.fromString(action).getTaskConsumer().accept(task);
        } catch (IllegalArgumentException e) {
            log.warn("执行task异常: {}", e.getMessage());
        }
    }
}
