package cn.iocoder.jtyf.module.semi.common.util;

import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.common.exception.CmnSysException;
import cn.iocoder.jtyf.module.semi.common.log.CmnLogger;
import cn.iocoder.jtyf.module.semi.common.msg.Message;
import cn.iocoder.jtyf.module.semi.common.msg.MessageConst;
import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.io.FilenameUtils;
import org.apache.taglibs.standard.tag.common.core.Util;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.*;
import java.net.URLEncoder;

public class WebUtil {
    /**
     * logger
     */
    private static final CmnLogger logger = CmnLogger.getLogger(WebUtil.class);

    public static String encodeTo(String str) {
        if (StrUtil.isEmpty(str)) {
            return "";
        }
        try {
            return new String(str.getBytes("UTF-8"), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // never occur when support UTF-8 and ISO-8859-1
            throw new CmnSysException(MessageConst.MSG_01990001, null, e);
        }
    }

    public static String decodeTo(String str) {
        if (StrUtil.isEmpty(str)) {
            return "";
        }
        try {
            return new String(str.getBytes("ISO-8859-1"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // never occur when support UTF-8 and ISO-8859-1
            throw new CmnSysException(MessageConst.MSG_01990001, null, e);
        }
    }

    /**
     * escapeXml (same as c:out)
     *
     * @param str
     *            source string
     * @return string was escaped
     */
    public static String escapeStringHtml(String str) {
        if (str == null) {
            return null;
        }
        return Util.escapeXml(str);
    }

    /**
     * escape indicated chars in string
     *
     * @param str
     *            source string
     * @param escChar
     *            chars want to be escaped
     * @return string was escaped
     */
    public static String escapeString(String str, char[] escChar) {
        if (str == null || escChar == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        char[] c = str.toCharArray();
        for (int i = 0; i < c.length; i++) {
            for (int j = 0; j < escChar.length; j++) {
                if (c[i] == escChar[j]) {
                    sb.append("\\");
                }
            }
            sb.append(c[i]);
        }
        return sb.toString();
    }

    public static String judgeDecode(String str) {
        try {
            if (StrUtil.isEmpty(str)) {
                return "";
            } else if(str.equals(new String(str.getBytes("ISO-8859-1"), "ISO-8859-1"))) {
                return decodeTo(str);
            } else if(str.equals(new String(str.getBytes("UTF-8"), "UTF-8"))) {
                return str;
            } else {
                throw new CmnSysException("decoding error");
            }
        } catch (UnsupportedEncodingException e) {
            // never occur when support UTF-8 and ISO-8859-1
            throw new CmnSysException(MessageConst.MSG_01990001, null, e);
        }
    }

    public static boolean isDownloadRequest(HttpServletRequest req) {
        String url = req.getRequestURL().toString();
        int idx = url.lastIndexOf("download");
        if (idx != -1) {
            url = url.substring(idx);
            return !url.contains("/");
        }
        return false;
    }

    public static boolean isPostRequest(HttpServletRequest req) {
        return RequestMethod.POST.name().equals(req.getMethod());
    }

    public static String convertWrap(String str) {
        if (StrUtil.isEmpty(str)) {
            return "";
        }
        if (str.split("\\n").length > 1) {
            str = str.replaceAll("\\n", "<br>&nbsp;&nbsp;");
        }
        return str;
    }

    public static String getWrapMessage(Message msg) {
        StringBuilder c = new StringBuilder();
        c.append(msg.getMsgContent());
        if (msg.getAddedMsgs() != null) {
            for (String detail : msg.getAddedMsgs()) {
                c.append("<br />&nbsp;&nbsp;");
                c.append(detail);
            }
        }
        return c.toString();
    }

    public static void downloadXlsx(HttpServletResponse response,
            String downloadFilename, String reportFilename) throws CmnBizException {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = new FileInputStream(downloadFilename);

            // response header
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader( "Content-Disposition", "attachment;filename="
                                + new String(reportFilename.getBytes("UTF-8"), "ISO8859-1"));

            int len;
            byte[] b = new byte[1024];

            // output
            outStream = response.getOutputStream();
            while ((len = inStream.read(b)) > 0) {
                outStream.write(b, 0, len);
            }

        } catch (ClientAbortException cae){
            logger.log(MessageConst.MSG_01000003, new Object[] {
                    "请求处理被中断。"}, cae);
            throw new CmnBizException(MessageConst.MSG_01000003, new Object[] {
                    "请求处理被中断。"});
        } catch (Exception e) {
            logger.log(MessageConst.MSG_01990001, null, e);
            throw new CmnBizException(MessageConst.MSG_01990001, null, e);
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
                if (inStream != null) {
                    inStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static void downloadCsv(HttpServletResponse response,
            String downloadFilename, String reportFilename) throws CmnBizException {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = new FileInputStream(downloadFilename);

            // response header
            response.reset();
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/comma-separated-values");
            response.setHeader( "Content-Disposition", "attachment;filename="
                                + new String(reportFilename.getBytes("UTF-8"), "ISO8859-1"));

            int len;
            byte[] b = new byte[1024];

            // output
            outStream = response.getOutputStream();
            while ((len = inStream.read(b)) > 0) {
                outStream.write(b, 0, len);
            }

        } catch (ClientAbortException cae){
            logger.log(MessageConst.MSG_01000003, new Object[] {
                    "请求处理被中断。"}, cae);
            throw new CmnBizException(MessageConst.MSG_01000003, new Object[] {
                    "请求处理被中断。"});
        } catch (Exception e) {
            logger.log(MessageConst.MSG_01990001, null, e);
            throw new CmnBizException(MessageConst.MSG_01990001, null, e);
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
                if (inStream != null) {
                    inStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static void outputBin(HttpServletResponse response,
            String filename, byte[] fileContent) throws CmnBizException {

        OutputStream outStream = null;
        try {
            // response header
             response.reset();
             setMimeTypeForBin(response, filename);

            // output
            outStream = response.getOutputStream();
            outStream.write(fileContent);
            outStream.flush();
        } catch (Exception e) {
            logger.log(MessageConst.MSG_01990001, null, e);
            throw new CmnBizException(MessageConst.MSG_01990001, null, e);
        } finally {
            try {
                if (outStream != null) {
                    outStream.close();
                }
            } catch (IOException e) {
            }
        }
    }

    public static void outputBin(HttpServletResponse response, String fullPathname) throws CmnBizException {
        outputBin(response, fullPathname, FileUtil.getFileBytes(fullPathname));
    }

    public static void setMimeTypeForBin(HttpServletResponse response, String filename) {
        String mimeType;
        switch (FilenameUtils.getExtension(filename).toLowerCase()) {
            case "xlsx":
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                break;
            case "xls":
                mimeType = "application/vnd.ms-excel";
                break;
            case "zip":
                mimeType = "application/zip";
                break;
            case "pdf":
                mimeType = "application/pdf";
                break;
            case "jpg":
                mimeType = "image/jpeg";
                break;
            case "png":
                mimeType = "image/png";
                break;
            case "gif":
                mimeType = "image/gif";
                break;
            case "html":
                mimeType = "text/html";
                break;
            case "js":
                mimeType = "application/javascript";
                break;
            case "css":
                mimeType = "text/css";
                break;
            case "svg":
                mimeType = "image/svg+xml";
                break;
            case "ico":
                mimeType = "image/x-icon";
                break;
            case "eot":
                mimeType = "application/vnd.ms-fontobject";
                break;
            case "json":
                mimeType = "application/json";
                break;
            case "ttf":
                mimeType = "font/ttf";
                break;
            case "woff":
                mimeType = "font/woff";
                break;
            case "woff2":
                mimeType = "font/woff2";
                break;
            case "wasm":
                mimeType = "application/wasm";
                break;
            default:
                mimeType = "application/octet-stream";
        }
        response.setContentType(mimeType);
    }

    public static void setMimeTypeForTxt(HttpServletResponse response, String filename) {
        String mimeType;
        switch (FilenameUtils.getExtension(filename).toLowerCase()) {
            case "csv":
                mimeType = "text/csv";
                break;
            case "tsv":
                mimeType = "text/tab-separated-values";
                break;
            default:
                mimeType = "text/*";
        }
        response.setContentType(mimeType);
    }

    public static void setDownloadFilename(HttpServletResponse response, String filename)
            throws UnsupportedEncodingException {
        String encodedFilename = URLEncoder.encode(filename, "UTF-8");
        encodedFilename = encodedFilename.replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + encodedFilename + ";filename*=UTF-8''" + encodedFilename);
    }

    /**
     * 拦截器[ApiAuthChkInterceptor]处理过的请求可以来调用，以取得登录用户信息。
     * 
     * @param req 请求
     * @return 用户信息
     */
    public static AccountInfo getLoginUser(HttpServletRequest req) {
        return (AccountInfo) req.getSession().getAttribute(ApiConst.SESSION_LOGIN_USER);
    }

    public static String getLoginUserAccount(HttpServletRequest req) {
        AccountInfo user = getLoginUser(req);
        if (user != null) {
            return user.getAccount();
        } else {
            return null;
        }
    }
}
