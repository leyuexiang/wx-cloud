package cn.iocoder.wx.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 团队列表情报
 */
public class TeamListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<TeamInfo> teamInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<TeamInfo> getTeamInfoList() {
        return teamInfoList;
    }

    public void setTeamInfoList(List<TeamInfo> teamInfoList) {
        this.teamInfoList = teamInfoList;
    }
}
