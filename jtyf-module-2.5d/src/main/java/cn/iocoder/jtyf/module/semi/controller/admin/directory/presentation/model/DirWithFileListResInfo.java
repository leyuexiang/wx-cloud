package cn.iocoder.jtyf.module.semi.controller.admin.directory.presentation.model;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件夹名称以及文件名称列表情报
 */
public class DirWithFileListResInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private List<String> directories;

    @JsonProperty
    private List<String> files;

    public List<String> getDirectories() {
        return directories;
    }

    public void setDirectories(List<String> directories) {
        this.directories = directories;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }
}
