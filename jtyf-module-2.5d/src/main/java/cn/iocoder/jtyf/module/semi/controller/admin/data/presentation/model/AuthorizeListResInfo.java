package cn.iocoder.jtyf.module.semi.controller.admin.data.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.data.businessinfo.AuthorizeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 权限列表情报
 */
public class AuthorizeListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<AuthorizeInfo> authInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<AuthorizeInfo> getAuthInfoList() {
        return authInfoList;
    }

    public void setAuthInfoList(List<AuthorizeInfo> authInfoList) {
        this.authInfoList = authInfoList;
    }
}
