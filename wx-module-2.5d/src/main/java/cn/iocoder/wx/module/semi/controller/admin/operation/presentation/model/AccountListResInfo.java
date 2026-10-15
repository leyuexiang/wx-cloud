package cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 账号列表情报
 */
public class AccountListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty
    private List<AccountInfo> list;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<AccountInfo> getList() {
        return list;
    }

    public void setList(List<AccountInfo> list) {
        this.list = list;
    }
}
