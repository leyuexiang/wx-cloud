package cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 角色情报
 */
public class RoleInfo implements Serializable {

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
     * 权限Code
     */
    private Integer authCd;

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

    public Integer getAuthCd() {
        return authCd;
    }

    public void setAuthCd(Integer authCd) {
        this.authCd = authCd;
    }
}
