package cn.iocoder.jtyf.module.semi.controller.admin.directory.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件夹情报
 */
public class DirInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @JsonProperty
    private String id;

    /**
     * 所有者ID
     */
    @JsonProperty
    private String ownerId;

    /**
     * 文件夹路径
     */
    @JsonProperty
    private String fullpath;

    /**
     * 创建时间
     */
    @JsonProperty
    private String createdAt;

    /**
     * 更新时间
     */
    @JsonProperty
    private String updatedAt;

    /**
     * 删除时间
     */
    @JsonProperty
    private String deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getFullpath() {
        return fullpath;
    }

    public void setFullpath(String fullpath) {
        this.fullpath = fullpath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }
}
