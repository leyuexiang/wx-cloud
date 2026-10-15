package cn.iocoder.jtyf.module.semi.controller.admin.message.presentation.form;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 消息API请求内容情报
 */
public class MessageReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * ID列表
     */
    @JsonProperty
    private List<String> ids;

    /**
     * From
     */
    @JsonProperty
    private String from;

    /**
     * Query
     */
    @JsonProperty(value = "q")
    private String query;

    /**
     * 读取状态:<br>
     * - true : 已读<br>
     * - null : 未读<br>
     * - false: 删除
     */
    @JsonProperty
    private Boolean read;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }
}
