package cn.iocoder.jtyf.module.semi.controller.admin.directory.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.jtyf.module.semi.controller.admin.directory.businessinfo.DirInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 文件夹列表情报
 */
public class DirListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<DirInfo> dirInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<DirInfo> getDirInfoList() {
        return dirInfoList;
    }

    public void setDirInfoList(List<DirInfo> dirInfoList) {
        this.dirInfoList = dirInfoList;
    }
}
