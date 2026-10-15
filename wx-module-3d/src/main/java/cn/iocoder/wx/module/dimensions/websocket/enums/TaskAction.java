package cn.iocoder.wx.module.dimensions.websocket.enums;

import cn.iocoder.wx.module.dimensions.producer.IKafkaScheduledTask;

import java.util.function.Consumer;

/**
 * @author akon
 * @desc 任务操作枚举
 * @date 2025-5-21
 */
public enum TaskAction {
    START("start", IKafkaScheduledTask::startTask),
    STOP("stop", IKafkaScheduledTask::stopTask);

    private final String action;

    private final Consumer<IKafkaScheduledTask> taskConsumer;

    TaskAction(String action, Consumer<IKafkaScheduledTask> taskConsumer) {
        this.action = action;
        this.taskConsumer = taskConsumer;
    }

    public static TaskAction fromString(String action) {
        for (TaskAction taskAction : TaskAction.values()) {
            if (taskAction.action.equalsIgnoreCase(action)) {
                return taskAction;
            }
        }
        throw new IllegalArgumentException("无效的操作指令: " + action);
    }

    public Consumer<IKafkaScheduledTask> getTaskConsumer() {
        return this.taskConsumer;
    }
}
