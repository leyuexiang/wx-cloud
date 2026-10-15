package cn.iocoder.wx.module.semi.controller.admin.data.businessinfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.iocoder.wx.module.semi.common.util.JsonUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import cn.iocoder.wx.module.semi.controller.admin.data.util.DataConst;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 数据情报
 */
public class DataInfo implements Serializable {

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
     * 数据名
     */
    @JsonProperty
    private String name;

    /**
     * 缩略图URL地址
     */
    @JsonProperty
    private String image;

    /**
     * 数据对象
     */
    @JsonProperty
    private Object data;

    /**
     * 数据字符串(Json格式)
     */
    @JsonIgnore
    private String dataJsonStr;

    /**
     * 类型
     */
    @JsonProperty
    private String type;

    /**
     * 所属文件夹
     */
    @JsonProperty
    private String folder;

    /**
     * 应用场景
     */
    @JsonProperty(value = "case")
    private String cs;

    /**
     * 数据类型标识<br>
     * true  - 模板数据<br>
     * false - 方案数据<br>
     */
    @JsonProperty
    private Boolean template;

    /**
     * 数据类型标识<br>
     * true  - 系统数据<br>
     * false - 用户数据<br>
     */
    @JsonProperty
    private Boolean system;

    /**
     * 是否共享
     */
    @JsonProperty
    private Boolean shared;

    /**
     * 推荐值
     */
    @JsonIgnore
    private Integer recommend;

    /**
     * 所属用户ID
     */
    @JsonProperty
    private String ownerId;

    /**
     * 所属用户名
     */
    @JsonProperty
    private String ownerName;

    /**
     * 编辑用户ID
     */
    @JsonProperty
    private String editorId;

    /**
     * 编辑用户名
     */
    @JsonProperty
    private String editorName;

    /**
     * 部门ID
     */
    @JsonProperty
    private String departmentId;

    /**
     * 团队列表
     */
    @JsonProperty(value = "teams")
    private List<String> teamList;

    /**
     * 团队列表
     */
    @JsonIgnore
    private String teams;

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

    public void buildOwnerAndEditor(String userid, String username, int authAct) {
        if (authAct != DataConst.AUTH_ACT_ADD && authAct != DataConst.AUTH_ACT_UPDATE) {
            return;
        }
        this.editorId = userid;
        this.editorName = username;
        if (authAct == DataConst.AUTH_ACT_ADD) {
            this.ownerId = userid;
            this.ownerName = username;
        }
    }

    public void buildDataObjectAndTeams() {
        if (StrUtil.isNotEmpty(this.dataJsonStr)) {
            this.data = JsonUtil.toObj(this.dataJsonStr, Object.class);
        }
        if (StrUtil.isNotEmpty(this.teams)) {
            this.teams = this.teams.replace("[", "")
                                   .replace("]", "")
                                   .replace("{", "")
                                   .replace("}", "")
                                   .replace("\"", "");
            if (StrUtil.isEmpty(this.teams.trim())) {
                this.teamList = new ArrayList<String>();
            } else {
                this.teamList = Arrays.asList(this.teams.split(","));
            }
        } else {
            this.teamList = new ArrayList<String>();
        }
    }

    public void buildDataJsonStr() {
        if (this.data != null) {
            this.dataJsonStr  = JsonUtil.toJsonStr(this.data);
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

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getDataJsonStr() {
        return dataJsonStr;
    }

    public void setDataJsonStr(String dataJsonStr) {
        this.dataJsonStr = dataJsonStr;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getCs() {
        return cs;
    }

    public void setCs(String cs) {
        this.cs = cs;
    }

    public Boolean getTemplate() {
        return template;
    }

    public void setTemplate(Boolean template) {
        this.template = template;
    }

    public Boolean getSystem() {
        return system;
    }

    public void setSystem(Boolean system) {
        this.system = system;
    }

    public Boolean getShared() {
        return shared;
    }

    public void setShared(Boolean shared) {
        this.shared = shared;
    }

    public Integer getRecommend() {
        return recommend;
    }

    public void setRecommend(Integer recommend) {
        this.recommend = recommend;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getEditorId() {
        return editorId;
    }

    public void setEditorId(String editorId) {
        this.editorId = editorId;
    }

    public String getEditorName() {
        return editorName;
    }

    public void setEditorName(String editorName) {
        this.editorName = editorName;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public List<String> getTeamList() {
        return teamList;
    }

    public void setTeamList(List<String> teamList) {
        this.teamList = teamList;
    }

    public String getTeams() {
        return teams;
    }

    public void setTeams(String teams) {
        this.teams = teams;
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
