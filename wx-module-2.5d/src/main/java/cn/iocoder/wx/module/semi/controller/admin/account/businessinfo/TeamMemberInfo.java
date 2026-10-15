package cn.iocoder.wx.module.semi.controller.admin.account.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 团队成员情报
 */
public class TeamMemberInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 团队ID
     */
    @JsonProperty
    private String teamId;

    /**
     * 用户ID
     */
    @JsonProperty
    private String userId;

    /**
     * 用户名
     */
    @JsonProperty
    private String username;

    /**
     * 管理角色<br>
     * 1  - 管理员<br>
     * 10 - 创建者<br>
     */
    @JsonProperty
    private Integer isAdmin;

    /**
     * 等待审批
     */
    @JsonProperty
    private Boolean pending;

    /**
     * 创建时间
     */
    @JsonProperty
    private String createdAt;

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Integer isAdmin) {
        this.isAdmin = isAdmin;
    }

    public Boolean getPending() {
        return pending;
    }

    public void setPending(Boolean pending) {
        this.pending = pending;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
