package cn.iocoder.wx.module.semi.controller.admin.account.presentation.form;

import java.io.Serializable;
import java.util.List;

import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.util.ApiConst;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 账号API请求内容情报
 */
public class AccountReqInfo implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * logger
     */
    private CmnLogger logger = CmnLogger.getLogger(this.getClass());

    /**
     * ID列表
     */
    @JsonProperty(value = "ids")
    private List<String> idList;

    /**
     * 账号
     */
    @JsonProperty
    private String account;

    /**
     * 电话
     */
    @JsonProperty
    private String phone;

    /**
     * 邮箱
     */
    @JsonProperty
    private String email;

    /**
     * 密码
     */
    @JsonProperty
    private String password;

    /**
     * 验证码(短信/邮件)/旧密码
     */
    @JsonProperty
    private String code;

    /**
     * 自动登录标识
     */
    @JsonProperty
    private Boolean remember;

    public AccountInfo rebuild() throws CmnBizException {
        AccountInfo result = new AccountInfo();
        result.setPassword(this.password);

        if (StrUtil.isNotEmpty(this.account)) {
            result.setAccount(this.account);
            result.setUsername(this.account);
            return result;
        }

        if (StrUtil.isNotEmpty(this.phone)) {
            if (!ApiConst.PATTERN_PHONE.matcher(this.phone).matches()) {
                Object[] args = new Object[] { this.phone };
                logger.log(AccountMessageConst.MSG_11030002, args);
                throw new CmnBizException(AccountMessageConst.MSG_11030002, args);
            }
            result.setAccount(this.phone);
            result.setPhone(this.phone);
            result.setUsername(this.phone);
            return result;
        }

        if (StrUtil.isNotEmpty(this.email)) {
            if (!ApiConst.PATTERN_EMAIL.matcher(this.email).matches()) {
                Object[] args = new Object[] { this.email };
                logger.log(AccountMessageConst.MSG_11030003, args);
                throw new CmnBizException(AccountMessageConst.MSG_11030003, args);
            }
            result.setAccount(this.email);
            result.setEmail(this.email);
            result.setUsername(this.email.substring(0, this.email.lastIndexOf("@")));
            return result;
        }

        logger.log(AccountMessageConst.MSG_11030001);
        throw new CmnBizException(AccountMessageConst.MSG_11030001);
    }

    public List<String> getIdList() {
        return idList;
    }

    public void setIdList(List<String> idList) {
        this.idList = idList;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getRemember() {
        return remember;
    }

    public void setRemember(Boolean remember) {
        this.remember = remember;
    }
}
