package cn.iocoder.wx.module.semi.controller.admin.account.presentation;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businesscontrol.AccountMngBusiCtrl;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.TeamMemberInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.conf.AccountConfParser;
import cn.iocoder.wx.module.semi.controller.admin.account.presentation.form.*;
import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import cn.iocoder.wx.module.semi.controller.admin.assets.conf.AssetsConfParser;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.msg.MessageConst;
import cn.iocoder.wx.module.semi.controller.admin.file.businessinfo.FileInfo;
import com.google.code.kaptcha.Constants;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping(value="api/account")
public class AccountMngController {

    private static final  String FUNC_NM = "账号管理机能";

    @Autowired
    private AccountMngBusiCtrl accountMngBusiCtrl;

    /**
     * 图片验证码
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/captcha", method=RequestMethod.GET)
    @PermitAll
    public String captcha(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "图片验证码", null);
        ApiUtil.setCaptchaResponse(req, res);
        return null;
    }

    /**
     * 邮件验证码
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/email", method=RequestMethod.POST)
    @PermitAll
    public String email(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "邮件验证码", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        EmailReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, EmailReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        String reqCode = reqInfo.getCode();
        if (StrUtil.isEmpty(reqCode)) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010001, null, false);
            return null;
        }

        // get session
        HttpSession session = req.getSession();

        String sesCode = (String) session.getAttribute(Constants.KAPTCHA_SESSION_KEY);
        Long sesTime = (Long) session.getAttribute(Constants.KAPTCHA_SESSION_DATE);
        if (StrUtil.isEmpty(sesCode) || sesTime == null) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        if (!reqCode.equalsIgnoreCase(sesCode)) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010001, null, false);
            return null;
        }

        long intervalSeconds = (new Date().getTime() - sesTime) / 1000;
        if (intervalSeconds > ApiConst.CAPTCHA_EXPIRY_SECONDS) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        // 生成数字验证码
        String code = ApiUtil.generateNumberCode(6);

        // 验证码用途种类
        String type = reqInfo.getType();

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);
        String username = loginUser != null ? (loginUser.getUsername() + "，") : "";

        try {
            // 发送邮件
            ApiUtil.sendCodeToMail(username, code, reqInfo.getEmail(), type);
        } catch (CmnBizException cbe) {
            ApiUtil.setErrorJsonResponse(res, cbe.getMsg().getMsgContent());
            return null;
        }

        if (ApiConst.EMAIL_CODE_TYPE_SIGNUP.equals(type)) {
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_SIGNUP, code);
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_SIGNUP, new Date().getTime());
        } else if (ApiConst.EMAIL_CODE_TYPE_FORGET.equals(type)) {
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_FORGET, code);
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_FORGET, new Date().getTime());
        } else if (ApiConst.EMAIL_CODE_TYPE_EMAIL.equals(type)) {
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_EMAIL, code);
            session.setAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_EMAIL, new Date().getTime());
        }

        session.removeAttribute(Constants.KAPTCHA_SESSION_KEY);
        session.removeAttribute(Constants.KAPTCHA_SESSION_DATE);

        ApiUtil.setEmptyJsonObjectResponse(res);
        return null;
    }

    /**
     * 用户注册
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/signup", method=RequestMethod.POST)
    @PermitAll
    public String signup(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "用户注册", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        String reqCode = reqInfo.getCode();
        if (StrUtil.isEmpty(reqCode)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "code"
                }, true);
            return null;
        }

        //取得[用户注册权限验证(动态验证码)是否禁用]设定值：
        //  设定值=true 的场合，验证码检证区分=图片验证码
        //  设定值=false的场合，验证码检证区分=邮件验证码
        boolean signupAuthCodeDisabled = AccountConfParser.getInstance().getSignupAuthCodeDisabled();

        // get session
        HttpSession session = req.getSession();

        String sesCode = null;
        Long sesTime = null;

        if (signupAuthCodeDisabled) {
            sesCode = (String) session.getAttribute(Constants.KAPTCHA_SESSION_KEY);
            sesTime = (Long) session.getAttribute(Constants.KAPTCHA_SESSION_DATE);
            
        } else {
            sesCode = (String) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_SIGNUP);
            sesTime = (Long) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_SIGNUP);
        }

        if (StrUtil.isEmpty(sesCode) || sesTime == null) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        if (!reqCode.equalsIgnoreCase(sesCode)) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010001, null, false);
            return null;
        }

        long intervalSeconds = (new Date().getTime() - sesTime) / 1000;
        if (intervalSeconds > ApiConst.CAPTCHA_EXPIRY_SECONDS) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        AccountInfo reqAccountInfo = null;
        AccountInfo resAccountInfo = null;
        try {
            // rebuild
            reqAccountInfo = reqInfo.rebuild();
            // 注册账号
            resAccountInfo = accountMngBusiCtrl.registerAccount(reqAccountInfo);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
            return null;
        }

        // 设置权限令牌
        resAccountInfo.setToken(JwtUtil.buildToken(resAccountInfo, 24));

        // clear session
        if (signupAuthCodeDisabled) {
            session.removeAttribute(Constants.KAPTCHA_SESSION_KEY);
            session.removeAttribute(Constants.KAPTCHA_SESSION_DATE);
        } else {
            session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_SIGNUP);
            session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_SIGNUP);
        }

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resAccountInfo));
        return null;
    }

    /**
     * 用户登录
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/login", method=RequestMethod.POST)
    @PermitAll
    public String login(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "用户登录", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getAccount()) || StrUtil.isEmpty(reqInfo.getPassword())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "account|password"
                }, true);
            
            return null;
        }

        AccountInfo resAccountInfo = null;
        try {
            // 登录账号
            resAccountInfo = accountMngBusiCtrl.login(reqInfo.getAccount(), reqInfo.getPassword());
            if (resAccountInfo == null) {
                throw new CmnBizException(AccountMessageConst.MSG_11040003);
            }
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
            return null;
        }

        // 设置权限令牌
        int expiresHours = 24;
        if (Boolean.TRUE.equals(reqInfo.getRemember())) {
            expiresHours = expiresHours * 365;
        }
        resAccountInfo.setToken(JwtUtil.buildToken(resAccountInfo, expiresHours));

        req.getSession().removeAttribute(ApiConst.SESSION_LOGIN_USER);
        req.getSession().setAttribute(ApiConst.SESSION_LOGIN_USER, resAccountInfo);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resAccountInfo));
        return null;
    }

    /**
     * 获取登录信息
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/profile", method=RequestMethod.GET)
    @PermitAll
    public String profile(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "获取登录信息", null);

        AccountInfo accountInfo = WebUtil.getLoginUser(req);
        if (accountInfo == null) {
            ApiUtil.setUnauthorizedResponse(req, res);
            return null;
        }
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfo));
        return null;
    }

    /**
     * 更新用户信息
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/profile/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:update')")
    public String updateProfile(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新用户信息", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        Map<String, String> reqMap = null;
        try {
            reqMap = JsonUtil.toMap(reqBody);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (reqMap.size() == 0) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 更新用户信息
        accountMngBusiCtrl.updateAccount(loginUser, reqMap);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 获取用户详细情报
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/detail")
    //@PreAuthorize("@ss.hasPermission('semi:account:detail')")
    public String detail(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "获取用户详细情报", null);

        AccountInfo accountInfo = WebUtil.getLoginUser(req);
        if (accountInfo == null) {
            ApiUtil.setUnauthorizedResponse(req, res);
            return null;
        }
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfo));
        return null;
    }

    /**
     * 获取统计情报
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/statistics")
    //@PreAuthorize("@ss.hasPermission('semi:account:statistics')")
    public String statistics(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "获取统计情报", null);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取统计情报
        Map<String, Integer> dataCountMap = accountMngBusiCtrl.getStatisticInfo(loginUser);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("userId", loginUser.getId());
        result.put("vipDataCount", dataCountMap);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(result));

        return null;
    }

    /**
     * 找回密码/修改密码
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/password/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:updatePassword')")
    public String updatePassword(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "找回密码/修改密码", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        // get session
        HttpSession session = req.getSession();

        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 是否找回密码标识
        boolean forget = loginUser == null ? true : false;

        if ((forget && StrUtil.isEmpty(reqInfo.getAccount()))
                || StrUtil.isEmpty(reqInfo.getCode())
                || StrUtil.isEmpty(reqInfo.getPassword())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "account(must when is forget)|code|password"
                }, true);
            return null;
        }

        if (forget) {
            //----------
            // 找回密码处理
            //----------

            String sesCode = (String) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_FORGET);
            Long sesTime = (Long) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_FORGET);

            if (StrUtil.isEmpty(sesCode) || sesTime == null) {
                ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
                return null;
            }

            if (!reqInfo.getCode().equalsIgnoreCase(sesCode)) {
                ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010001, null, false);
                return null;
            }

            long intervalSeconds = (new Date().getTime() - sesTime) / 1000;
            if (intervalSeconds > ApiConst.CAPTCHA_EXPIRY_SECONDS) {
                ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
                return null;
            }
        } else {
            //----------
            // 修改密码处理
            //----------

            // 补足参数：账号
            if (reqInfo.getAccount() == null) {
                reqInfo.setAccount(loginUser.getAccount());
            }
        }

        AccountInfo resAccountInfo = null;
        try {
            // 更新密码
            resAccountInfo = accountMngBusiCtrl.updatePassword(
                    reqInfo.getAccount(), reqInfo.getCode(), reqInfo.getPassword(), forget);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
            return null;
        }

        // 设置权限令牌
        resAccountInfo.setToken(JwtUtil.buildToken(resAccountInfo, 24));

        // clear session
        if (forget) {
            session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_FORGET);
            session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_FORGET);
        }

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resAccountInfo));

        return null;
    }

    /**
     * 更新邮箱
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/email/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:updateEmail')")
    public String updateEmail(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新邮箱", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getEmail()) || StrUtil.isEmpty(reqInfo.getCode())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "email|code"
                }, true);
            return null;
        }

        // get session
        HttpSession session = req.getSession();

        String sesCode = (String) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_EMAIL);
        Long sesTime = (Long) session.getAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_EMAIL);

        if (StrUtil.isEmpty(sesCode) || sesTime == null) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        if (!reqInfo.getCode().equalsIgnoreCase(sesCode)) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010001, null, false);
            return null;
        }

        long intervalSeconds = (new Date().getTime() - sesTime) / 1000;
        if (intervalSeconds > ApiConst.CAPTCHA_EXPIRY_SECONDS) {
            ApiUtil.setErrorJsonResponse(res, AccountMessageConst.MSG_11010002, null, false);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setId(loginUser.getId());
        accountInfo.setEmail(reqInfo.getEmail());
        if (loginUser.isEmailAccount()) {
            accountInfo.setAccount(reqInfo.getEmail());
            accountInfo.setUsername(reqInfo.getEmail().substring(0, reqInfo.getEmail().lastIndexOf("@")));
        }

        try {
            // 更新邮箱
            accountMngBusiCtrl.updateEmail(accountInfo);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
            return null;
        }

        // clear session
        session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_VALUE_EMAIL);
        session.removeAttribute(ApiConst.SESSION_DYNAMIC_CODE_DATE_EMAIL);

        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 获取公司部门列表
     * 
     * @param req 请求
     * @param res 响应
     * @param q 部门名称
     * @param p 父部门ID
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/departments")
    //@PreAuthorize("@ss.hasPermission('semi:account:departments')")
    public String departments(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "p", required = false) String p,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"q\": " + q + ", \"p\": " + p + ", \"current\": " + current + ", \"pageSize\": " + pageSize;

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取公司部门列表", reqUrlParam);

        // 取得登录用户详细情报
        // AccountInfo loginUser = WebUtil.getLoginUser(req);

        // if (loginUser == null || StrUtil.isEmpty(loginUser.getDepartmentId())) {
        //     ApiUtil.setForbiddenResponse(req, res);
        //     return null;
        // }

        // 获取公司部门列表
        DepartmentListResInfo departmentInfoList = accountMngBusiCtrl.getDepartmentInfoList(
                q, p, current, pageSize);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(departmentInfoList));

        return null;
    }

    /**
     * 获取用户头像
     * 
     * @param req 请求
     * @param res 响应
     * @param id 用户ID
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/avatar/{id}", method=RequestMethod.GET)
    @PermitAll
    public String avatar(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("id") String id) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"id\": " + id;

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取用户头像", reqUrlParam);

        String appWebRoot = AssetsConfParser.getInstance().getAppWebAssetsRoot();

        // 获取账号详细情报
        AccountInfo accountInfo = null;
        try {
            accountInfo = accountMngBusiCtrl.getAccountDetailById(id, false);
        } catch (CmnBizException e) {
            // do nothing
        }

        if (accountInfo == null || StrUtil.isEmpty(accountInfo.getAvatarUrl())) {
            try {
                WebUtil.outputBin(res, appWebRoot + "/img/avatar.png");
            } catch (CmnBizException e) {
                if (e.getMsg().getCode().equals(MessageConst.MSG_01030001)) {
                    ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
                } else {
                    throw e;
                }
            }
            return null;
        }

        // 获取头像URL
        String avatarUrl = accountInfo.getAvatarUrl();

        if (avatarUrl.startsWith("http")) {
            return "redirect:" + accountInfo.getAvatarUrl();
        }

        try {
            if (avatarUrl.startsWith("/api/file/")) {
                avatarUrl = avatarUrl.replaceFirst("/api/file/", "");
            } else if (avatarUrl.startsWith("/api/image/")) {
                avatarUrl = avatarUrl.replaceFirst("/api/image/", "");
            }

            // 获取文件
            FileInfo fileInfo = accountMngBusiCtrl.getFileInfo(accountInfo, avatarUrl);

            // output
            WebUtil.outputBin(res, fileInfo.getName(), fileInfo.getContent());
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 获取用户注册选项信息
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/signup/options", method=RequestMethod.GET)
    //@PreAuthorize("@ss.hasPermission('semi:account:signupOptions')")
    public String signupOptions(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "获取用户注册选项信息", null);
        Map<String, Object> resultMap = new HashMap<String, Object>();
        resultMap.put("authCode", !AccountConfParser.getInstance().getSignupAuthCodeDisabled());
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resultMap));
        return null;
    }

    /**
     * 查找用户
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/find", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:findUser')")
    public String findUser(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "查找用户", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getAccount())
                && StrUtil.isEmpty(reqInfo.getPhone())
                && StrUtil.isEmpty(reqInfo.getEmail())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "account|phone|email"
                }, true);
            return null;
        }

        try {
            // 查找用户
            AccountInfo accountInfo = accountMngBusiCtrl.findUser(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfo));
        } catch (CmnBizException e) {
            ApiUtil.setEmptyJsonObjectResponse(res);
        }

        return null;
    }

    /**
     * 查找用户列表
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/names", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:findUserNames')")
    public String findUserNames(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "查找用户列表", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo.getIdList())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "ids"
                }, true);
            return null;
        }

        // 查找用户列表
        List<AccountInfo> accountInfoList = accountMngBusiCtrl.findUserList(reqInfo);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfoList));

        return null;
    }

    /**
     * 获取团队列表
     * 
     * @param req 请求
     * @param res 响应
     * @param q 团队名称
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/teams")
    //@PreAuthorize("@ss.hasPermission('semi:account:teams')")
    public String teams(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"q\": " + q + ", \"current\": " + current + ", \"pageSize\": " + pageSize;
        ApiUtil.callApiLog(req, FUNC_NM, "获取团队列表", reqUrlParam);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取团队列表
        TeamListResInfo teamInfoList = accountMngBusiCtrl.getTeamInfoList(
                loginUser, q, current, pageSize);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamInfoList));

        return null;
    }

    /**
     * 获取团队成员列表
     * 
     * @param req 请求
     * @param res 响应
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/members", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:teamMembers')")
    public String teamMembers(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "获取团队成员列表", reqUrlParam + ", " + reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamMemberInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamMemberInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        // 获取团队成员列表
        TeamMemberListResInfo teamMemberInfoList = accountMngBusiCtrl.getTeamMemberInfoList(
                reqInfo.getTeamId(), reqInfo.getUsername(), current, pageSize);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamMemberInfoList));

        return null;
    }

    /**
     * 创建团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/create", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:creaeTeam')")
    public String createTeam(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "创建团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getName())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "name"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 创建团队
            TeamInfo teamInfo = accountMngBusiCtrl.createTeam(loginUser, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:updatTeam')")
    public String updateTeam(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "更新团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "id"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 更新团队
            TeamInfo teamInfo = accountMngBusiCtrl.updateTeam(loginUser, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:deleteTeam')")
    public String deleteTeam(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "删除团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "id"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 删除团队
        accountMngBusiCtrl.deleteTeam(loginUser, reqInfo.getId());
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 转让团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/owner", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:transferTeam')")
    public String transferTeam(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "转让团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamMemberInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamMemberInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getTeamId()) || StrUtil.isEmpty(reqInfo.getUserId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "teamId|userId"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 转让团队
            TeamInfo teamInfo = accountMngBusiCtrl.transferTeam(loginUser, reqInfo.getTeamId(), reqInfo.getUserId());
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新团队管理员
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/admin", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:updateTeamAdmin')")
    public String updateTeamAdmin(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "更新团队管理员", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamMemberInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamMemberInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getTeamId()) || StrUtil.isEmpty(reqInfo.getUserId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "teamId|userId"
                }, true);
            return null;
        }

        if (reqInfo.getIsAdmin() != null && reqInfo.getIsAdmin().intValue() > 1) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010009, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "isAdmin", reqInfo.getIsAdmin()
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 更新团队管理员
            TeamInfo teamInfo = accountMngBusiCtrl.updateTeamAdmin(loginUser, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(teamInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 邀请用户加入团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/invite", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:teamInvite')")
    public String teamInvite(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "邀请用户加入团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getTeamId()) || StrUtil.isEmpty(reqInfo.getTeamName())
                || StrUtil.isEmpty(reqInfo.getMemberId()) || StrUtil.isEmpty(reqInfo.getMemberName())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "teamId|teamName|memberId|memberName"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 邀请用户加入团队
            accountMngBusiCtrl.teamInvite(loginUser, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 申请加入团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/join/{teamId}", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:joinTeam')")
    public String joinTeam(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("teamId") String teamId) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "申请加入团队", null);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 申请加入团队
            boolean pending = accountMngBusiCtrl.joinTeam(loginUser, teamId);
            Map<String, Boolean> resDataMap = new HashMap<String, Boolean>();
            resDataMap.put("pending", pending);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resDataMap));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 审核加入团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/accept", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:teamAccept')")
    public String teamAccept(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "审核加入团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getTeamId()) || StrUtil.isEmpty(reqInfo.getUserId()) || reqInfo.getAccept() == null) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "teamId|userId|accept"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 审核加入团队
        accountMngBusiCtrl.teamAccept(loginUser, reqInfo);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 移除团队成员或退出团队
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/team/member/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:account:removeTeamMember')")
    public String removeTeamMember(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "移除团队成员或退出团队", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        TeamMemberInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, TeamMemberInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getTeamId()) || StrUtil.isEmpty(reqInfo.getUserId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "teamId|userId"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 移除团队成员或退出团队
        accountMngBusiCtrl.removeTeamMember(loginUser, reqInfo);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }
}
