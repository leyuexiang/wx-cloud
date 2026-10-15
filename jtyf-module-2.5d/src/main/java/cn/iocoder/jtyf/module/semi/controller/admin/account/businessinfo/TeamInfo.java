package cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.jtyf.module.semi.common.util.ListUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 团队情报
 */
public class TeamInfo implements Serializable {

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
     * 名称
     */
    @JsonProperty
    private String name;

    /**
     * 头像URL
     */
    @JsonProperty
    private String avatarUrl;

    /**
     * 描述
     */
    @JsonProperty
    private String description;

    /**
     * 创建者ID
     */
    @JsonProperty
    private String ownerId;

    /**
     * 是否开启邀请链接
     */
    @JsonProperty
    private Boolean inviteLink;

    /**
     * 邀请链接有效期
     */
    @JsonProperty
    private String inviteExpiration;

    /**
     * 邀请是否需要审核通过
     */
    @JsonProperty
    private Boolean inviteApprove;

    /**
     * 创建时间
     */
    @JsonProperty
    private String createdAt;

    @JsonProperty
    private Integer isAdmin;

    /**
     * 成员列表
     */
    @JsonProperty(value = "members")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<TeamMemberInfo> memberList;

    public void buildIsAdmin() {
        if (ListUtil.isEmptyList(memberList) || memberList.size() > 1) {
            return;
        }
        this.isAdmin = memberList.get(0).getIsAdmin();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Boolean getInviteLink() {
        return inviteLink;
    }

    public void setInviteLink(Boolean inviteLink) {
        this.inviteLink = inviteLink;
    }

    public String getInviteExpiration() {
        return inviteExpiration;
    }

    public void setInviteExpiration(String inviteExpiration) {
        this.inviteExpiration = inviteExpiration;
    }

    public Boolean getInviteApprove() {
        return inviteApprove;
    }

    public void setInviteApprove(Boolean inviteApprove) {
        this.inviteApprove = inviteApprove;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(Integer isAdmin) {
        this.isAdmin = isAdmin;
    }

    public List<TeamMemberInfo> getMemberList() {
        return memberList;
    }

    public void setMemberList(List<TeamMemberInfo> memberList) {
        this.memberList = memberList;
    }
}
