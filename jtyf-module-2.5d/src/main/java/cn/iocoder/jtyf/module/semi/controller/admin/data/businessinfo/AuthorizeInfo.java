package cn.iocoder.jtyf.module.semi.controller.admin.data.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 权限情报
 */
public class AuthorizeInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    @JsonProperty
    private String dataId;

    @JsonProperty
    private String subjectId;

    @JsonProperty
    private int subjectType;

    @JsonProperty
    private int action;

    @JsonProperty
    private String attributes;

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public int getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(int subjectType) {
        this.subjectType = subjectType;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }
}
