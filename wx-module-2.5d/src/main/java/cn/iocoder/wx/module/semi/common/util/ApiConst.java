package cn.iocoder.wx.module.semi.common.util;

import cn.iocoder.wx.module.semi.common.prop.CmnPropertiesUtils;

import java.util.regex.Pattern;


public interface ApiConst {

    public static final String JWT_SECRET = "1QAz0OKm";

    public static final String EMPTY_JSON_OBJECT = "{}";

    public static final String EMPTY_JSON_ARRAY = "[]";

    public static final String API_CORS_LIST_FILE = CmnPropertiesUtils.getConfPath() + "api/conf/cors_list.conf";

    public static final String API_AUTH_FILE = CmnPropertiesUtils.getConfPath() + "api/conf/auth.xml";

    public static final String ACCOUNT_FILE = CmnPropertiesUtils.getConfPath() + "account/conf/account.conf";

    public static final String ACCOUNT_EMAIL_FILE = CmnPropertiesUtils.getConfPath() + "account/conf/email.conf";

    public static final String ACCOUNT_EMAIL_TEMPLATE_FILE = CmnPropertiesUtils.getConfPath() + "account/conf/email_template.html";

    public static final String ASSETS_FILE = CmnPropertiesUtils.getConfPath() + "assets/conf/assets.conf";

    public static final String ENTERPRISE_FILE = CmnPropertiesUtils.getConfPath() + "assets/conf/enterprise.json";

    /**
     * 验证码有效期(秒数)
     */
    public static final int CAPTCHA_EXPIRY_SECONDS = 300;

    /**
     * 邮件验证码用途种类：注册
     */
    public static final String EMAIL_CODE_TYPE_SIGNUP = "signup";

    /**
     * 邮件验证码用途种类：找回密码
     */
    public static final String EMAIL_CODE_TYPE_FORGET = "forget";

    /**
     * 邮件验证码用途种类：修改邮箱
     */
    public static final String EMAIL_CODE_TYPE_EMAIL = "email";

    /**
     * session attribute name：登录用户
     */
    public static final String SESSION_LOGIN_USER = "SESSION_LOGIN_USER";

    /**
     * session attribute name：动态验证码生成数值(注册)
     */
    public static final String SESSION_DYNAMIC_CODE_VALUE_SIGNUP = "SESSION_DYNAMIC_CODE_VALUE_SIGNUP";

    /**
     * session attribute name：动态验证码生成日期(注册)
     */
    public static final String SESSION_DYNAMIC_CODE_DATE_SIGNUP = "SESSION_DYNAMIC_CODE_DATE_SIGNUP";

    /**
     * session attribute name：动态验证码生成数值(找回密码)
     */
    public static final String SESSION_DYNAMIC_CODE_VALUE_FORGET = "SESSION_DYNAMIC_CODE_VALUE_FORGET";

    /**
     * session attribute name：动态验证码生成日期(找回密码)
     */
    public static final String SESSION_DYNAMIC_CODE_DATE_FORGET = "SESSION_DYNAMIC_CODE_DATE_FORGET";

    /**
     * session attribute name：动态验证码生成数值(修改邮箱)
     */
    public static final String SESSION_DYNAMIC_CODE_VALUE_EMAIL = "SESSION_DYNAMIC_CODE_VALUE_EMAIL";

    /**
     * session attribute name：动态验证码生成日期(修改邮箱)
     */
    public static final String SESSION_DYNAMIC_CODE_DATE_EMAIL = "SESSION_DYNAMIC_CODE_DATE_EMAIL";

    /**
     * Pattern: Phone
     */
    public static final Pattern PATTERN_PHONE = Pattern.compile("^(1)\\d{10}$");

    /**
     * Pattern: Email
     */
    public static final Pattern PATTERN_EMAIL = Pattern.compile("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
}
