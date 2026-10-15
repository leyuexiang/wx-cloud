package cn.iocoder.wx.module.semi.controller.admin.assets.presentation.form;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 资源API请求内容情报
 */
public class AssetsReqInfo implements Serializable {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 路径
     */
    @JsonProperty
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
