package cn.iocoder.wx.module.semi.controller.admin.data.businessinfo;

import java.io.Serializable;

import cn.iocoder.wx.module.semi.common.util.JsonUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 历史情报
 */
public class HistoryInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty(value = "historyId")
    private String id;

    @JsonProperty
    private String dataId;

    @JsonProperty
    private String name;

    @JsonProperty
    private String image;

    @JsonProperty
    private Object data;

    @JsonIgnore
    private String dataJsonStr;

    @JsonProperty
    private String remarks;

    @JsonProperty
    private String tag;

    @JsonProperty
    private String ownerId;

    @JsonProperty
    private String createdAt;

    public void buildDataObject() {
        if (StrUtil.isEmpty(this.dataJsonStr)) {
            return;
        }
        this.data = JsonUtil.toObj(this.dataJsonStr, Object.class);
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

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
