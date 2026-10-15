package cn.iocoder.wx.module.dimensions.websocket.message;

import lombok.Data;

/**
 * 示例：client -> server 发送消息
 *
 * @author 九天芸菲
 */
@Data
public class GeneralSendMessage {

    /**
     * 业务类型
     */
    private String businessModule;

    /**
     * 内容
     */
    private Object text;

}
