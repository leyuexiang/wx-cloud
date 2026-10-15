package cn.iocoder.wx.module.semi.common.util;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;


import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.log.CmnLogger;
import cn.iocoder.wx.module.semi.common.msg.MessageConst;
import cn.iocoder.wx.module.semi.common.msg.MsgResourceManager;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.conf.EmailConfParser;
import cn.iocoder.wx.module.semi.controller.admin.account.util.AccountMessageConst;
import com.google.code.kaptcha.Constants;
import com.google.code.kaptcha.Producer;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.mail.HtmlEmail;


public class ApiUtil {
    private static final CmnLogger logger = CmnLogger.getLogger(ApiUtil.class);

    public static boolean isAllowedOrigin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String origin = req.getHeader("Origin");
        if (StrUtil.isEmpty(origin)) {
            return true;
        }

//        List<String> allowedOrigins = CorsListConfParser.getInstance().getCorsList();
//        if (allowedOrigins.contains(origin)) {
//            res.setHeader("Access-Control-Allow-Origin", origin);
//            res.setHeader("Access-Control-Allow-Credentials", "true");
//            res.setHeader("X-Content-Type-Options", "nosniff");
//            return true;
//        } else {
//            logger.log(ApiMessageConst.MSG_10010003,new Object[] {
//                    req.getRemoteUser() == null ? "" : req.getRemoteUser(),
//                    origin,
//                    getApiPath(req)
//            });
//            setErrorJsonResponse(res, MsgResourceManager.getMessage(
//                    ApiMessageConst.MSG_10010003, new Object[] {
//                            req.getRemoteUser() == null ? "" : req.getRemoteUser(),
//                            origin,
//                            getApiPath(req)
//            }));
//            return false;
//        }

        res.setHeader("Access-Control-Allow-Origin", origin);
        res.setHeader("Access-Control-Allow-Credentials", "true");
        res.setHeader("X-Content-Type-Options", "nosniff");
        return true;
    }

    public static void callApiLog(HttpServletRequest req, String funcNm, String actNm, String param){
        String userAccount = WebUtil.getLoginUserAccount(req);
        userAccount = userAccount == null ? "" : userAccount;
        StringBuffer paramStr = new StringBuffer(StrUtil.safeToStr(param));
        String msgLog = MessageFormat.format(
                "['{'{0}'}'],'{'{1}'}','{'{2}'}',params='{'{3}'}'",
                userAccount, funcNm, actNm, paramStr.toString());
        logger.log(MessageConst.MSG_01000001, new Object[]{ msgLog });
    }

    public static String readTxtBody(HttpServletRequest req) throws IOException {
        StringBuffer result = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = req.getReader();
            String line = null;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    public static byte[] readBinBody(HttpServletRequest req) throws IOException {
        int length = req.getContentLength();
        byte[] result = new byte[length];
        ServletInputStream in = null;
        try {
            in = req.getInputStream();
            in.read(result, 0, length);
            return result;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    public static void setBadRequestResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // res.setContentType("text/html");
        res.setContentType("application/json");
        res.sendError(HttpServletResponse.SC_BAD_REQUEST);       // 400
        logger.log(ApiMessageConst.MSG_10010001, new Object[] {
                getApiPath(req)
        });
    }

    public static void setUnauthorizedResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // res.setContentType("text/html");
        res.setContentType("application/json");
        res.sendError(HttpServletResponse.SC_UNAUTHORIZED);      // 401
    }

    public static void setForbiddenResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // res.setHeader("WWW-Authenticate","JDBC realm=" + req.getAuthType());
        // res.setContentType("text/html");
        res.setContentType("application/json");
        res.sendError(HttpServletResponse.SC_FORBIDDEN);         // 403
        logger.log(ApiMessageConst.MSG_10010002, new Object[] {
                req.getRemoteUser() == null ? "" : req.getRemoteUser(),
                getApiPath(req)
        });
    }

    public static void setNotFoundResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // res.setContentType("text/html");
        res.setContentType("application/json");
        res.sendError(HttpServletResponse.SC_NOT_FOUND);         // 404
        logger.log(ApiMessageConst.MSG_10010004, new Object[] {
                getApiPath(req)
        });
    }

    public static void setConflictResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        // res.setContentType("text/html");
        res.setContentType("application/json");
        res.sendError(HttpServletResponse.SC_CONFLICT);          // 409
        logger.log(ApiMessageConst.MSG_10010005, new Object[] {
                getApiPath(req)
        });
    }

    public static void setJsonResponse(HttpServletResponse res, String jsonStr) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(jsonStr);
    }

    public static void setEmptyJsonObjectResponse(HttpServletResponse res) throws IOException {
        setJsonResponse(res, ApiConst.EMPTY_JSON_OBJECT);
    }

    public static void setEmptyJsonArrayResponse(HttpServletResponse res) throws IOException {
        setJsonResponse(res, ApiConst.EMPTY_JSON_ARRAY);
    }

    public static void setErrorJsonResponse(HttpServletResponse res, String errorMsg) throws IOException {
        setJsonResponse(res, buildErrorMsgJson(errorMsg));
    }

    public static void setErrorJsonResponse(HttpServletResponse res,
            String messageCode,  Object[] args, boolean outputLog) throws IOException {
        if (outputLog) {
            logger.log(messageCode, args);
        }
        setErrorJsonResponse(res, MsgResourceManager.getMessage(messageCode, args));
    }

    public static void setDetailErrorJsonResponse(HttpServletResponse res,
            String summaryErrorMsg, String detailErrorMsg) throws IOException {
        setJsonResponse(res, buildDetailErrorMsgJson(summaryErrorMsg, detailErrorMsg));
    }

    public static void setHtmlResponse(HttpServletResponse res, String htmlStr) throws IOException {
        res.setContentType("text/html");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(htmlStr);
    }

    /**
     * 生成图片验证码
     * 
     * @param req 请求
     * @param res 响应
     * @throws IOException IO异常
     */
    public static void setCaptchaResponse(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Producer captchaProducer = SpringUtil.getBean(Producer.class, "captchaProducer");

        String txt = captchaProducer.createText();
        BufferedImage img = captchaProducer.createImage(txt);

        HttpSession session = req.getSession();
        session.setAttribute(Constants.KAPTCHA_SESSION_KEY, txt);
        session.setAttribute(Constants.KAPTCHA_SESSION_DATE, new Date().getTime());

        ServletOutputStream out = null;
        try {
            res.setDateHeader("Expires", 0);
            res.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            res.addHeader("Cache-Control", "post-check=0, pre-check=0");
            res.setHeader("Pragma", "no-cache");
            res.setContentType("image/jpeg");

            out = res.getOutputStream();
            ImageIO.write(img, "jpg", out);
            out.flush();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                // do nothing
            }
        }
    }

    /**
     * 发送邮件(动态验证码)
     * 
     * @param username 用户名
     * @param code 验证码
     * @param toAddress 收件地址
     * @param codeUseType 验证码用途种类<br>
     *            signup - 注册<br>
     *            forget - 找回密码<br>
     *            email  - 修改邮箱
     * @throws CmnBizException
     * @throws Exception 异常
     */
    public static void sendCodeToMail(String username, String code, String toAddress, String codeUseType) throws Exception {
        String msg = Files.readString(Paths.get(ApiConst.ACCOUNT_EMAIL_TEMPLATE_FILE));
        // 用户名
        msg = msg.replace("@username@", username);
        // 验证码
        msg = msg.replace("@code@", code);

        EmailConfParser conf = EmailConfParser.getInstance();
        String host = conf.getHost();
        int port = Integer.valueOf(conf.getPort());
        String from = conf.getFrom();
        String authentication = conf.getAuthentication();
        String charset = conf.getCharset();
        String subject = null;

        if (ApiConst.EMAIL_CODE_TYPE_SIGNUP.equals(codeUseType)) {
            subject = conf.getSubjectTypeSignup();
        } else if (ApiConst.EMAIL_CODE_TYPE_FORGET.equals(codeUseType)) {
            subject = conf.getSubjectTypeForget();
        } else if (ApiConst.EMAIL_CODE_TYPE_EMAIL.equals(codeUseType)) {
            subject = conf.getSubjectTypeEmail();
        } else {
            Object[] args = new Object[] { codeUseType };
            logger.log(AccountMessageConst.MSG_11020001, args);
            throw new CmnBizException(AccountMessageConst.MSG_11020001, args);
        }

        HtmlEmail mail = new HtmlEmail();
        mail.setHostName(host);
        mail.setSmtpPort(port);
        mail.setSSLOnConnect(true);
        mail.setFrom(from);
        mail.setAuthentication(from, authentication);
        mail.addTo(toAddress);
        mail.setCharset(charset);
        mail.setSubject(subject);
        mail.setMsg(msg);

        mail.send();
    }

    /**
     * 获取JSON格式字符串中指定项目的值
     * 
     * @param data JSON格式字符串
     * @param key 项目
     * @return 值
     */
    public static String getKeyStrVal(String data, String key) {
        if (StrUtil.isEmpty(data)) {
            return null;
        }
        if (!data.contains("\"" + key + "\"")) {
            return null;
        }
        int index = data.indexOf("\"" + key + "\"");
        data = data.substring(index + 1);
        index = data.indexOf("\"");
        data = data.substring(index + 1);
        index = data.indexOf("\"");
        if (index == -1) {
            return null;
        }
        data = data.substring(index + 1);
        index = data.indexOf("\"");
        if (index == -1) {
            return null;
        }
        data = data.substring(0, index);
        return data;
    }

    /**
     * 获取JSON格式字符串中指定项目的值
     * 
     * @param data JSON格式字符串
     * @param key 项目
     * @return 值
     */
    public static Object getKeyObjVal(String data, String key) {
        if (StrUtil.isEmpty(data)) {
            return null;
        }
        Object obj = JsonUtil.toObj(data, Object.class);
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, ?> map = (LinkedHashMap<String, ?>) obj;
        return map.get(key);
    }

    /**
     * 生成新建/编辑数据(文件夹,自定义组件,图纸...)的用户信息JSON字符串
     * 
     * @param req 请求
     * @param addFlg 数据新增标识
     * @return 用户信息JSON字符串
     */
    public static String buildUserJsonStr(HttpServletRequest req, boolean addFlg) {
        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);
        if (loginUser == null) {
            return null;
        }
        String id = loginUser.getId();
        String username = loginUser.getUsername();
        StringBuffer sb = new StringBuffer();
        sb.append("{");
        if (addFlg) {
            sb.append("\"owner\":{");
        } else {
            sb.append("\"editor\":{");
        }
        sb.append("\"id\":\"").append(id).append("\",");
        sb.append("\"name\":\"").append(username).append("\"");
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    /**
     * 生成数字验证码
     * 
     * @param length 长度(位数)
     * @return 数字验证码
     */
    public static String generateNumberCode(int length) {
        if (length <= 0) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(String.valueOf(random.nextInt(10)));
        }
        return sb.toString();
    }

    public static String getApiPath(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo() == null ? "" : req.getPathInfo();
        return WebUtil.decodeTo(servletPath + pathInfo);
    }

    public static String buildJson(List<String[]> kvList) {
        if (ListUtil.isEmptyList(kvList)) {
            return ApiConst.EMPTY_JSON_OBJECT;
        }

        String[] item;
        String key = null;
        String val = null;

        StringBuffer sb = new StringBuffer();
        sb.append("{");
        for (int i = 0; i < kvList.size(); i++) {
            item = kvList.get(i);
            key = item[0];
            val = item[1];

            if (i != 0) {
                sb.append(", ");
            }
            sb.append("\"").append(key).append("\"");
            sb.append(": ");
            if ((val.startsWith("{") && val.endsWith("}")) || (val.startsWith("[") && val.endsWith("]"))) {
                sb.append(val);
            } else {
                sb.append("\"").append(val).append("\"");
            }
        }
        sb.append("}");

        return sb.toString();
    }

    private static String buildErrorMsgJson(String errorMsg) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"error\":\"");
        sb.append(errorMsg);
        sb.append("\"}");
        return sb.toString();
    }

    private static String buildDetailErrorMsgJson(String summaryErrorMsg, String detailErrorMsg) {
        StringBuffer sb = new StringBuffer();
        sb.append("{\"error\":\"");
        sb.append(summaryErrorMsg);
        sb.append("\",\"detail\":\"");
        sb.append(detailErrorMsg);
        sb.append("\"}");
        return sb.toString();
    }
}
