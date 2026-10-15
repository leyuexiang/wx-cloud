package cn.iocoder.wx.module.semi.controller.admin.data.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 数据列表情报
 */
public class DataListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<DataInfo> dataInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<DataInfo> getDataInfoList() {
        return dataInfoList;
    }

    public void setDataInfoList(List<DataInfo> dataInfoList) {
        this.dataInfoList = dataInfoList;
    }
}
