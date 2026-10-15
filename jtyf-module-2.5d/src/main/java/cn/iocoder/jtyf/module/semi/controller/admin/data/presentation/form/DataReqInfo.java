package cn.iocoder.jtyf.module.semi.controller.admin.data.presentation.form;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import cn.iocoder.jtyf.module.semi.common.util.DateUtil;
import cn.iocoder.jtyf.module.semi.common.util.ListUtil;
import cn.iocoder.jtyf.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 数据API请求内容情报
 */
public class DataReqInfo implements Serializable {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * ID列表
     */
    @JsonProperty(value = "ids")
    private List<String> idList;

    /**
     * 历史ID列表
     */
    @JsonProperty(value = "historyIds")
    private List<String> historyIdList;

    /**
     * ID
     */
    @JsonProperty
    private String id;

    /**
     * 数据ID
     */
    @JsonProperty
    private String dataId;

    /**
     * 履历ID
     */
    @JsonProperty
    private String historyId;

    /**
     * 数据名
     */
    @JsonProperty
    private String name;

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
     * 标签
     */
    @JsonProperty
    private String tag;

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
     * 部门ID
     */
    @JsonProperty
    private String departmentId;

    /**
     * 团队ID
     */
    @JsonProperty
    private String teamId;

    /**
     * 创建开始时间
     */
    @JsonProperty
    private Long createdStart;

    /**
     * 创建结束时间
     */
    @JsonProperty
    private Long createdEnd;

    /**
     * 更新开始时间
     */
    @JsonProperty
    private Long updatedStart;

    /**
     * 更新结束时间
     */
    @JsonProperty
    private Long updatedEnd;

    /**
     * 创建开始时间
     */
    @JsonProperty
    private String createdStartStr;

    /**
     * 创建结束时间
     */
    @JsonProperty
    private String createdEndStr;

    /**
     * 更新开始时间
     */
    @JsonProperty
    private String updatedStartStr;

    /**
     * 更新结束时间
     */
    @JsonProperty
    private String updatedEndStr;

    /**
     * 检索字段
     */
    @JsonProperty
    private String projection;

    /**
     * 检索字段List
     */
    @JsonIgnore
    private List<String> projList;

    /**
     * 排序规则
     */
    @JsonProperty
    private Map<String, Integer> sort;

    public void buildSelectSql(String attributes, boolean list, boolean history) {
        if (StrUtil.isEmpty(this.projection)) {
            if (attributes.equals("*")) {
                // 默认查询规则
                if (!history) {
                    if (list) {
                        this.projection = "id, name, image, type, folder, case, template, system, shared, owner_id, owner_name, editor_id, editor_name, department_id, teams, created_at, updated_at";
                    } else {
                        this.projection = "id, name, image, data, type, folder, case, template, system, shared, owner_id, owner_name, editor_id, editor_name, department_id, teams, created_at, updated_at";
                    }
                } else {
                    this.projection = "id, data_id, name, image, data, remarks, tag, owner_id, created_at";
                }
            } else {
                this.projection = attributes;
            }
        } else {
            if (attributes.equals("*")) {
                // nothing
            } else {
                List<String> pList = Arrays.asList(projection.split(","));
                List<String> aList = Arrays.asList(attributes.split(","));
                for (String a : aList) {
                    a = a.trim();
                }
                List<String> result = new ArrayList<String>();
                for (String p : pList) {
                    if (aList.contains(p.trim())) {
                        result.add(p.trim());
                    }
                }
                if (ListUtil.isNotEmptyList(result)) {
                    this.projection = StrUtil.strArrAggWithoutEmpty(result.toArray(new String[result.size()]), ", ");
                }
            }
        }
        if (StrUtil.isNotEmpty(this.projection)) {
            this.projList = new ArrayList<String>();
            for (String column : this.projection.split(",")) {
                column = column.replaceAll("\"", "");
                projList.add(column.trim());
            }
        }
    }

    public void buildWhereSql() {
        if (this.createdStart != null) {
            this.createdStartStr = DateUtil.toStr(new Date(this.createdStart));
        }
        if (this.createdEnd != null) {
            this.createdEndStr = DateUtil.toStr(new Date(this.createdEnd));
        }
        if (this.updatedStart != null) {
            this.updatedStartStr = DateUtil.toStr(new Date(this.updatedStart));
        }
        if (this.updatedEnd != null) {
            this.updatedEndStr = DateUtil.toStr(new Date(this.updatedEnd));
        }
    }

    public String buildSortSql(boolean operation) {
        if (this.sort == null || this.sort.isEmpty()) {
            // 默认排序规则
            if ((this.shared != null && this.shared.booleanValue())
                    || (this.system != null && this.system.booleanValue())
                    || operation) {
                return "recommend DESC, updated_at DESC";
            }
            return "updated_at DESC";
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

    public List<String> getIdList() {
        return idList;
    }

    public void setIdList(List<String> idList) {
        this.idList = idList;
    }

    public List<String> getHistoryIdList() {
        return historyIdList;
    }

    public void setHistoryIdList(List<String> historyIdList) {
        this.historyIdList = historyIdList;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
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

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
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

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public Long getCreatedStart() {
        return createdStart;
    }

    public void setCreatedStart(Long createdStart) {
        this.createdStart = createdStart;
    }

    public Long getCreatedEnd() {
        return createdEnd;
    }

    public void setCreatedEnd(Long createdEnd) {
        this.createdEnd = createdEnd;
    }

    public Long getUpdatedStart() {
        return updatedStart;
    }

    public void setUpdatedStart(Long updatedStart) {
        this.updatedStart = updatedStart;
    }

    public Long getUpdatedEnd() {
        return updatedEnd;
    }

    public void setUpdatedEnd(Long updatedEnd) {
        this.updatedEnd = updatedEnd;
    }

    public String getCreatedStartStr() {
        return createdStartStr;
    }

    public void setCreatedStartStr(String createdStartStr) {
        this.createdStartStr = createdStartStr;
    }

    public String getCreatedEndStr() {
        return createdEndStr;
    }

    public void setCreatedEndStr(String createdEndStr) {
        this.createdEndStr = createdEndStr;
    }

    public String getUpdatedStartStr() {
        return updatedStartStr;
    }

    public void setUpdatedStartStr(String updatedStartStr) {
        this.updatedStartStr = updatedStartStr;
    }

    public String getUpdatedEndStr() {
        return updatedEndStr;
    }

    public void setUpdatedEndStr(String updatedEndStr) {
        this.updatedEndStr = updatedEndStr;
    }

    public String getProjection() {
        return projection;
    }

    public void setProjection(String projection) {
        this.projection = projection;
    }

    public List<String> getProjList() {
        return projList;
    }

    public void setProjList(List<String> projList) {
        this.projList = projList;
    }

    public Map<String, Integer> getSort() {
        return sort;
    }

    public void setSort(Map<String, Integer> sort) {
        this.sort = sort;
    }
}
