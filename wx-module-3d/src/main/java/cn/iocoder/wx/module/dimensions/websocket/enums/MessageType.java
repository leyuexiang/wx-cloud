package cn.iocoder.wx.module.dimensions.websocket.enums;

import lombok.Getter;

/**
 * @desc websocket消息类型
 * @author akon
 * @date 2025-5-21
 */
@Getter
public enum MessageType {
    // 客户端发送类型
    TASK_EVENT_SEND("task-event-message-send"),
    GENERAL_MESSAGE_SEND("general-message-send"),

    // 服务器返回类型
    TASK_EVENT_RECEIVE("task-event-message-receive"),
    GENERAL_MESSAGE_RECEIVE("general-message-receive"),
    ERROR_RESPONSE("error-response");

    private final String type;

    MessageType(String type) {
        this.type = type;
    }

    // 根据类型字符串查找枚举值
    public static MessageType fromType(String type) {
        for (MessageType value : values()) {
            if (value.type.equals(type)) {
                return value;
            }
        }
        throw new IllegalArgumentException("未知的消息类型: " + type);
    }
}