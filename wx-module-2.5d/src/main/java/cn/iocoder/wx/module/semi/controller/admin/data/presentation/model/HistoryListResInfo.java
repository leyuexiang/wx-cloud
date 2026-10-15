package cn.iocoder.wx.module.semi.controller.admin.data.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.HistoryInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 历史列表情报
 */
public class HistoryListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<HistoryInfo> historyInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<HistoryInfo> getHistoryInfoList() {
        return historyInfoList;
    }

    public void setHistoryInfoList(List<HistoryInfo> historyInfoList) {
        this.historyInfoList = historyInfoList;
    }
}
