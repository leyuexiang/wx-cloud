package cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 角色API请求内容情报
 */
public class RoleReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 角色相关字段(名称)检索条件
     */
    @JsonProperty(value = "q")
    private String query;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
