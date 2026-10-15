package cn.iocoder.wx.module.semi.controller.admin.account.businessinfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.util.ApiConst;
import cn.iocoder.wx.module.semi.common.util.ListUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 账号情报
 */
public class AccountInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * logger
     */
    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    /**
     * ID
     */
    @JsonProperty
    private String id;

    /**
     * 账号
     */
    @JsonProperty
    private String account;

    /**
     * 电话
     */
    @JsonProperty
    private String phone;

    /**
     * 邮箱
     */
    @JsonProperty
    private String email;

    /**
     * 用户名
     */
    @JsonProperty
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 头像URL
     */
    @JsonProperty
    private String avatarUrl;

    /**
     * 角色列表
     */
    @JsonProperty(value = "roles")
    private List<String> roleList;

    /**
     * 角色名列表
     */
    @JsonProperty(value = "roleNames")
    private List<String> roleNmList;

    /**
     * 角色名列表字符串
     */
    @JsonIgnore
    private String roleNmsStr;

    /**
     * 角色权限Code值列表
     */
    @JsonIgnore
    private List<Long> roleAuthCdList;

    /**
     * 部门ID
     */
    @JsonProperty
    private String departmentId;

    /**
     * 部门名称
     */
    @JsonProperty
    private String departmentName;

    /**
     * 团队ID列表
     */
    @JsonProperty
    private List<String> teams;

    /**
     * 团队情报列表
     */
    @JsonProperty
    private List<TeamInfo> teamList;

    /**
     * 团队ID列表字符串
     */
    @JsonIgnore
    private String teamsStr;

    /**
     * 创建时间
     */
    @JsonProperty
    private String createdAt;

    /**
     * 更新时间
     */
    @JsonProperty
    private String updatedAt;

    /**
     * 删除时间
     */
    @JsonProperty
    private String deletedAt;

    /**
     * 身份认证token
     */
    @JsonProperty
    private String token;

    public void buildTeams() {
        if (ListUtil.isEmptyList(this.teamList)) {
            return;
        }
        this.teams = new ArrayList<String>();
        for (TeamInfo teamInfo : this.teamList) {
            teams.add(teamInfo.getId());
        }
    }

    @JsonIgnore
    public boolean isEmailAccount() {
        if (StrUtil.isEmpty(this.account) || StrUtil.isEmpty(this.email)) {
            return false;
        }
        return this.account.equals(this.email);
    }

    public void rebuildAccountInfo() throws CmnBizException {
        if (ListUtil.isNotEmptyList(this.roleList)) {
            this.roleNmsStr = StrUtil.join(this.roleList, ",", "\"");
        }
        if (ListUtil.isNotEmptyList(this.teams)) {
            this.teamsStr = StrUtil.join(this.teams, ",", "\"");
        }

        if (StrUtil.isNotEmpty(this.phone)) {
            if (!ApiConst.PATTERN_PHONE.matcher(this.phone).matches()) {
                Object[] args = new Object[] { this.phone };
                logger.log(AccountMessageConst.MSG_11030002, args);
                throw new CmnBizException(AccountMessageConst.MSG_11030002, args);
            }
        }

        if (StrUtil.isNotEmpty(this.email)) {
            if (!ApiConst.PATTERN_EMAIL.matcher(this.email).matches()) {
                Object[] args = new Object[] { this.email };
                logger.log(AccountMessageConst.MSG_11030003, args);
                throw new CmnBizException(AccountMessageConst.MSG_11030003, args);
            }
        }

        if (StrUtil.isNotEmpty(this.account)) {
            this.username = this.account;
            return;
        }

        if (StrUtil.isNotEmpty(this.phone)) {
            this.account = this.phone;
            this.username = this.phone;
        } else if (StrUtil.isNotEmpty(this.email)) {
            this.account = this.email;
            this.username = this.email.substring(0, this.email.lastIndexOf("@"));
        }
    }

    public boolean checkRequiredUpdateFieldsNotEmpty() throws CmnBizException {
        if (ListUtil.isNotEmptyList(this.roleList)) {
            return true;
        }
        if (StrUtil.isNotEmpty(this.departmentId)) {
            return true;
        }
        if (ListUtil.isNotEmptyList(this.teams)) {
            return true;
        }
        return false;
    }

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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public List<String> getRoleList() {
        return roleList;
    }

    public void setRoleList(List<String> roleList) {
        this.roleList = roleList;
    }

    public List<String> getRoleNmList() {
        return roleNmList;
    }

    public void setRoleNmList(List<String> roleNmList) {
        this.roleNmList = roleNmList;
    }

    public String getRoleNmsStr() {
        return roleNmsStr;
    }

    public void setRoleNmsStr(String roleNmsStr) {
        this.roleNmsStr = roleNmsStr;
    }

    public List<Long> getRoleAuthCdList() {
        return roleAuthCdList;
    }

    public void setRoleAuthCdList(List<Long> roleAuthCdList) {
        this.roleAuthCdList = roleAuthCdList;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public List<String> getTeams() {
        return teams;
    }

    public void setTeams(List<String> teams) {
        this.teams = teams;
    }

    public List<TeamInfo> getTeamList() {
        return teamList;
    }

    public void setTeamList(List<TeamInfo> teamList) {
        this.teamList = teamList;
    }

    public String getTeamsStr() {
        return teamsStr;
    }

    public void setTeamsStr(String teamsStr) {
        this.teamsStr = teamsStr;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
