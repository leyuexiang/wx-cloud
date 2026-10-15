package cn.iocoder.jtyf.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 部门列表情报
 */
public class DepartmentListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<DepartmentInfo> departmentInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<DepartmentInfo> getDepartmentInfoList() {
        return departmentInfoList;
    }

    public void setDepartmentInfoList(List<DepartmentInfo> departmentInfoList) {
        this.departmentInfoList = departmentInfoList;
    }
}
