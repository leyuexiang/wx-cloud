package cn.iocoder.wx.module.semi.controller.admin.directory.presentation.form;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 文件夹API请求内容情报
 */
public class DirReqInfo implements Serializable {
    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 文件夹路径
     */
    @JsonProperty
    private String fullpath;

    /**
     * 数据删除状态<br>
     * 0     - 未删除<br>
     * 1     - 删除<br>
     * Other - 全部<br>
     */
    @JsonProperty
    private int deleted;

    /**
     * 旧文件夹路径
     */
    @JsonProperty
    private String oldFullpath;

    /**
     * 新文件夹路径
     */
    @JsonProperty
    private String newFullpath;

    /**
     * 文件夹路径列表
     */
    @JsonProperty
    private List<String> fullpaths;

    /**
     * 是否物理删除标识
     */
    @JsonProperty
    private boolean physically;

    /**
     * 排序规则
     */
    @JsonProperty
    private Map<String, Integer> sort;

    public String buildSortSql() {
        if (this.sort == null || this.sort.isEmpty()) {
            // 默认排序规则
            return "fullpath ASC";
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

    public String getFullpath() {
        return fullpath;
    }

    public void setFullpath(String fullpath) {
        this.fullpath = fullpath;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public String getOldFullpath() {
        return oldFullpath;
    }

    public void setOldFullpath(String oldFullpath) {
        this.oldFullpath = oldFullpath;
    }

    public String getNewFullpath() {
        return newFullpath;
    }

    public void setNewFullpath(String newFullpath) {
        this.newFullpath = newFullpath;
    }

    public List<String> getFullpaths() {
        return fullpaths;
    }

    public void setFullpaths(List<String> fullpaths) {
        this.fullpaths = fullpaths;
    }

    public boolean isPhysically() {
        return physically;
    }

    public void setPhysically(boolean physically) {
        this.physically = physically;
    }
}
