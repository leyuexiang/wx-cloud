package cn.iocoder.jtyf.framework.websocket.core.sender.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * Kafka 广播 WebSocket 的消息
 *
 * @author 九天芸菲
 */
@Data
public class KafkaWebSocketMessage {

    /**
     * Session 编号
     */
    @JsonIgnore
    private String sessionId;
    /**
     * 用户类型
     */
    @JsonIgnore
    private Integer userType;
    /**
     * 用户编号
     */
    @JsonIgnore
    private Long userId;

    /**
     * 消息类型
     */
    @JsonIgnore
    private String messageType;
    /**
     * 消息内容
     */
    @JsonIgnore
    private String messageContent;

    /////////////////////////////以下是具体的业务数据/////////////////////////////////

    /**
     * 版本
     */
    private String version;

    /**
     * 类型
     */
    private String type;

    /**
     * 具体数据
     */
    private Object data;

}
