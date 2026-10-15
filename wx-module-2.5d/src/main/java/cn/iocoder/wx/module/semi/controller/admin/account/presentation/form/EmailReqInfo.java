package cn.iocoder.wx.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 邮件验证码API请求内容情报
 */
public class EmailReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    @JsonProperty
    private String email;

    /**
     * 验证码
     */
    @JsonProperty
    private String code;

    /**
     * 用途种类
     */
    @JsonProperty
    private String type;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
