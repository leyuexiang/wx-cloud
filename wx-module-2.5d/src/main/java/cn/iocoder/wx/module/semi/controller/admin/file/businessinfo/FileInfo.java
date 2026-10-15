package cn.iocoder.wx.module.semi.controller.admin.file.businessinfo;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import cn.iocoder.wx.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 文件情报
 */
public class FileInfo implements Serializable {

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
     * 文件名
     */
    @JsonProperty
    private String name;

    /**
     * 类型
     */
    @JsonProperty
    private String type;

    /**
     * 文件全名
     */
    @JsonProperty
    private String fullname;

    /**
     * URL
     */
    @JsonProperty
    private String url;

    /**
     * 文件夹
     */
    @JsonProperty
    private String directory;

    /**
     * 标签列表
     */
    @JsonIgnore
    private String tags;

    /**
     * 标签列表
     */
    @JsonProperty(value = "tags")
    private List<String> tagList;

    /**
     * 备注
     */
    @JsonProperty
    private String remarks;

    /**
     * 是否共享
     */
    @JsonProperty
    private boolean shared;

    /**
     * 文件字节码内容
     */
    @JsonIgnore
    private byte[] content;

    /**
     * 文件字节码大小
     */
    @JsonProperty
    private long size;

    /**
     * 所属文件夹是否删除
     */
    @JsonProperty
    private boolean directoryDeleted;

    /**
     * 所有者ID
     */
    @JsonProperty
    private String ownerID;

    /**
     * 所有者名
     */
    @JsonProperty
    private String ownerName;

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

    public void buildTagList() {
        if (StrUtil.isEmpty(this.tags)) {
            return;
        }
        this.tags = this.tags.replace("[", "")
                             .replace("]", "")
                             .replace("{", "")
                             .replace("}", "")
                             .replace("\"", "");
        if (StrUtil.isEmpty(this.tags)) {
            return;
        }
        this.tagList = Arrays.asList(StrUtil.splitAll(this.tags, ","));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public List<String> getTagList() {
        return tagList;
    }

    public void setTagList(List<String> tagList) {
        this.tagList = tagList;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isDirectoryDeleted() {
        return directoryDeleted;
    }

    public void setDirectoryDeleted(boolean directoryDeleted) {
        this.directoryDeleted = directoryDeleted;
    }

    public String getOwnerID() {
        return ownerID;
    }

    public void setOwnerID(String ownerID) {
        this.ownerID = ownerID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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
