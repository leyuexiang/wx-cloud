package cn.iocoder.jtyf.module.dimensions.websocket.message;

import lombok.Data;

/**
 * 示例：server -> client 同步消息
 *
 * @author 九天芸菲
 */
@Data
public class TaskEventReceiveMessage {

    /**
     * 接收人的编号
     */
    private Long fromUserId;
    /**
     * 内容
     */
    private String text;

    /**
     * 是否单聊
     */
    private Boolean single;

}
