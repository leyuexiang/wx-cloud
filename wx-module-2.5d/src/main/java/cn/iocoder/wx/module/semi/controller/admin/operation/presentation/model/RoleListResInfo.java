package cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo.RoleInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 角色列表情报
 */
public class RoleListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty
    private List<RoleInfo> list;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<RoleInfo> getList() {
        return list;
    }

    public void setList(List<RoleInfo> list) {
        this.list = list;
    }
}
