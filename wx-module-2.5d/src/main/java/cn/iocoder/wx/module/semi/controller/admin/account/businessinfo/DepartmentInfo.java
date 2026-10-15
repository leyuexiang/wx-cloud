package cn.iocoder.wx.module.semi.controller.admin.account.businessinfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import cn.iocoder.wx.module.semi.common.util.ListUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 部门情报
 */
public class DepartmentInfo implements Serializable {

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
     * 名称
     */
    @JsonProperty
    private String name;

    /**
     * 父ID
     */
    @JsonProperty
    private String parentId;

    /**
     * 全路径列表
     */
    @JsonProperty(value = "fullpath")
    private List<String> fullpathList;

    /**
     * 全路径字符串
     */
    @JsonIgnore
    private String fullpaths;

    /**
     * 领导人
     */
    @JsonProperty
    private String leader;

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
    @JsonIgnore
    private String deletedAt;

    public void buildFullpathInfo() {
        if (this.fullpathList == null) {
            this.fullpathList = new ArrayList<String>();
        }
        if (StrUtil.isNotEmpty(id)) {
            this.fullpathList.add(id);
        }
        buildFullpaths();
    }

    public void buildFullpaths() {
        if (ListUtil.isNotEmptyList(this.fullpathList)) {
            this.fullpaths = StrUtil.join(this.fullpathList, ",", "'");
        }
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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public List<String> getFullpathList() {
        return fullpathList;
    }

    public void setFullpathList(List<String> fullpathList) {
        this.fullpathList = fullpathList;
    }

    public String getFullpaths() {
        return fullpaths;
    }

    public void setFullpaths(String fullpaths) {
        this.fullpaths = fullpaths;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
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
