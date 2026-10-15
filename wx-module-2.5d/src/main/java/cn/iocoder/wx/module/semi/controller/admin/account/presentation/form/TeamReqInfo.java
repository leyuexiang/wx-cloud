package cn.iocoder.wx.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 团队API请求内容情报
 */
public class TeamReqInfo implements Serializable {

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
     * 团队名称
     */
    @JsonProperty
    private String teamName;

    /**
     * 成员ID
     */
    @JsonProperty
    private String memberId;

    /**
     * 成员名称
     */
    @JsonProperty
    private String memberName;

    /**
     * 用户ID
     */
    @JsonProperty
    private String userId;

    /**
     * 是否同意
     */
    @JsonProperty
    private Boolean accept;

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Boolean getAccept() {
        return accept;
    }

    public void setAccept(Boolean accept) {
        this.accept = accept;
    }
}
