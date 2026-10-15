package cn.iocoder.jtyf.module.semi.controller.admin.message.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 消息读取情报
 */
public class MessageReadInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    @JsonProperty
    private String messageId;

    /**
     * 用户ID
     */
    @JsonProperty
    private String userId;

    /**
     * 读取状态:<br>
     * - true : 已读<br>
     * - null : 未读<br>
     * - false: 删除
     */
    @JsonProperty
    private Boolean read;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }
}
