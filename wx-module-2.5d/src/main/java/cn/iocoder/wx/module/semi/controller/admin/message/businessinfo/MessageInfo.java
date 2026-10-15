package cn.iocoder.wx.module.semi.controller.admin.message.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 消息情报
 */
public class MessageInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @JsonProperty
    private String id;

    /**
     * From
     */
    @JsonProperty
    private String from;

    /**
     * To
     */
    @JsonProperty
    private String to;

    /**
     * Title
     */
    @JsonProperty
    private String title;

    /**
     * HTML
     */
    @JsonProperty
    private String html;

    /**
     * Data
     */
    @JsonProperty
    private String data;

    /**
     * 创建时间
     */
    @JsonProperty
    private String createdAt;

    /**
     * 删除时间
     */
    @JsonProperty
    private String deletedAt;

    /**
     * 读取状态:<br>
     * - true : 已读<br>
     * - null : 未读<br>
     * - false: 删除
     */
    @JsonProperty
    private Boolean read;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }
}
