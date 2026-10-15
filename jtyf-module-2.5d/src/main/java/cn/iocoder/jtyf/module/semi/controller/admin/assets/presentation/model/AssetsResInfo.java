package cn.iocoder.jtyf.module.semi.controller.admin.assets.presentation.model;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 资源列表情报
 */
public class AssetsResInfo implements Serializable {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty
    private List<String> list;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<String> getList() {
        return list;
    }

    public void setList(List<String> list) {
        this.list = list;
    }
}
