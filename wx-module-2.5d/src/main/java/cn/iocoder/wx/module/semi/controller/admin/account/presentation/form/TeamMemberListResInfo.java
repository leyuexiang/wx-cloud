package cn.iocoder.wx.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamMemberInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 团队成员列表情报
 */
public class TeamMemberListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<TeamMemberInfo> teamMemberInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<TeamMemberInfo> getTeamMemberInfoList() {
        return teamMemberInfoList;
    }

    public void setTeamMemberInfoList(List<TeamMemberInfo> teamMemberInfoList) {
        this.teamMemberInfoList = teamMemberInfoList;
    }
}
