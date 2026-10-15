package cn.iocoder.wx.module.dimensions.websocket.message;

import lombok.Data;

/**
 * 示例：server -> client 同步消息
 *
 * @author 九天芸菲
 */
@Data
public class GeneralReceiveMessage {

    /**
     * 主题
     */
    private String topic;

    /**
     * 内容
     */
    private String text;


}
