package cn.iocoder.wx.module.semi.controller.admin.file.presentation.model;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.file.businessinfo.FileInfo;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 文件列表情报
 */
public class FileListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private int total;

    @JsonProperty(value = "list")
    private List<FileInfo> fileInfoList;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<FileInfo> getFileInfoList() {
        return fileInfoList;
    }

    public void setFileInfoList(List<FileInfo> fileInfoList) {
        this.fileInfoList = fileInfoList;
    }
}
