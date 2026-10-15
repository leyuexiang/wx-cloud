package cn.iocoder.wx.module.semi.controller.admin.file.presentation.form;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件API请求内容情报
 */
public class FileReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    
    /**
     * 文件全名列表
     */
    @JsonProperty(value = "fullnames")
    private List<String> fullnameList;

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
     * 文件夹
     */
    @JsonProperty
    private String directory;

    /**
     * 是否包含子文件夹文件
     */
    @JsonProperty
    private boolean children;

    /**
     * 标签
     */
    @JsonProperty
    private String tag;

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
    private Boolean shared;

    /**
     * 数据删除状态<br>
     * 0 - 未删除<br>
     * 1 - 删除<br>
     * Other - 全部<br>
     */
    @JsonProperty
    private int deleted;

    /**
     * 排序规则
     */
    @JsonProperty
    private Map<String, Integer> sort;

    /**
     * 是否物理删除
     */
    @JsonProperty
    private boolean physically;

    public String buildSortSql() {
        if (this.sort == null || this.sort.isEmpty()) {
            // 默认排序规则
            return "owner_id ASC, created_at DESC";
        }
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, Integer> entry : this.sort.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            if (value.intValue() > 0) {
                sb.append(", " + key + " ASC");
            }
            if (value.intValue() < 0) {
                sb.append(", " + key + " DESC");
            }
        }
        return sb.toString().substring(2);
    }

    public List<String> getFullnameList() {
        return fullnameList;
    }

    public void setFullnameList(List<String> fullnameList) {
        this.fullnameList = fullnameList;
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

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public boolean isChildren() {
        return children;
    }

    public void setChildren(boolean children) {
        this.children = children;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    public Boolean getShared() {
        return shared;
    }

    public void setShared(Boolean shared) {
        this.shared = shared;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public Map<String, Integer> getSort() {
        return sort;
    }

    public void setSort(Map<String, Integer> sort) {
        this.sort = sort;
    }

    public boolean isPhysically() {
        return physically;
    }

    public void setPhysically(boolean physically) {
        this.physically = physically;
    }
}
