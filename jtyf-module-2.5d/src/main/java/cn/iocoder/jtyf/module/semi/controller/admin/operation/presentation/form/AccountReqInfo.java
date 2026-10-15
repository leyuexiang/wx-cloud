package cn.iocoder.jtyf.module.semi.controller.admin.operation.presentation.form;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 账号API请求内容情报
 */
public class AccountReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 账号ID
     */
    @JsonProperty
    private String id;

    /**
     * 账号名
     */
    @JsonProperty
    private String account;

    /**
     * 账号名称相关字段(账号名,手机号,邮箱名,用户名)检索条件
     */
    @JsonProperty(value = "q")
    private String query;

    /**
     * 部门ID
     */
    @JsonProperty(value = "department")
    private String departmentId;

    /**
     * 团队ID
     */
    @JsonProperty(value = "team")
    private String teamId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }
}
