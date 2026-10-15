package cn.iocoder.jtyf.module.semi.controller.admin.file.presentation;

import cn.iocoder.jtyf.module.semi.common.util.*;
import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.controller.admin.file.businesscontrol.FileMngBusiCtrl;
import cn.iocoder.jtyf.module.semi.controller.admin.file.businessinfo.FileInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.model.FileListResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@RestController
@RequestMapping(value="api/file")
public class FileMngController {

    private static final String FUNC_NM = "文件管理机能";

    @Autowired
    private FileMngBusiCtrl fileMngBusiCtrl;

    /**
     * 获取文件列表
     * 
     * @param req 请求
     * @param res 响应
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:file:list')")
    public String list(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取文件列表", reqUrlParam + ", " + reqBody);

        FileReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, FileReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (reqInfo == null) {
            reqInfo = new FileReqInfo();
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取文件列表
        FileListResInfo fileInfoList = fileMngBusiCtrl.getFileInfoList(
                loginUser, reqInfo, current, pageSize);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(fileInfoList));

        return null;
    }

    /**
     * 上传文件
     * 
     * @param req 请求
     * @param res 响应
     * @param file 文件
     * @param name 文件名
     * @param type 文件类型
     * @param filename 文件名
     * @param directory 文件目录
     * @param tags 标签
     * @param remarks 备注
     * @param share 是否共享
     * @param conflict 文件名冲突规则(null or "" - 覆盖;new - 重命名;error or 其它 - 返回错误)
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/upload", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:file:upload')")
    public String upload(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "file", required = true) MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "directory", required = false) String directory,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "remarks", required = false) String remarks,
            @RequestParam(value = "share", required = false) Boolean share,
            @RequestParam(value = "conflict", required = false) String conflict) throws Exception {
        // 读取请求Form内容
        StringBuffer reqBody = new StringBuffer();
        reqBody.append("{");
        reqBody.append("\"name\": \"" + name + "\", ");
        reqBody.append("\"type\" :\"" + type + "\", ");
        reqBody.append("\"filename\" :\"" + filename + "\", ");
        reqBody.append("\"directory\" :\"" + directory + "\", ");
        reqBody.append("\"tags\" :\"" + tags + "\", ");
        reqBody.append("\"remarks\" :\"" + remarks + "\", ");
        reqBody.append("\"share\" :" + share + ", ");
        reqBody.append("\"conflict\" :\"" + conflict + "\"");
        reqBody.append("}");

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "上传文件", reqBody.toString());

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 没有指定的场合，默认不共享
        if (share == null) {
            share = false;
        }

        // 文件名重命名标识
        boolean renameFileFlg = false;

        try {
            // 保存文件
            FileInfo fileInfo = fileMngBusiCtrl.saveFileInfo(loginUser,
                    file, name, type, filename, directory, tags, remarks, share, conflict, false);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(fileInfo));
        } catch (Exception e) {
            if (e instanceof DataAccessException || e instanceof TransactionException) {
                if ("new".equals(conflict)) {
                    renameFileFlg = true;
                } else {
                    throw e;
                }
            } else if (e instanceof CmnBizException) {
                ApiUtil.setErrorJsonResponse(res, ((CmnBizException) e).getMsg().getMsgContent());
            } else {
                throw e;
            }
        }

        if (renameFileFlg) {
            // 保存文件
            FileInfo fileInfo = fileMngBusiCtrl.saveFileInfo(loginUser,
                    file, name, type, filename, directory, tags, remarks, share, conflict, true);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(fileInfo));
        }

        return null;
    }

    /**
     * 获取文件
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.GET)
    //@PreAuthorize("@ss.hasPermission('semi:file:getFile')")
    public String getFile(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取文件", fullname);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        FileInfo fileInfo = null;
        try {
            // 获取文件
            fileInfo = fileMngBusiCtrl.getFileInfo(loginUser, fullname);
        } catch (CmnBizException e) {
            ApiUtil.setNotFoundResponse(req, res);
            return null;
        }

        // output
        WebUtil.outputBin(res, fileInfo.getName(), fileInfo.getContent());

        return null;
    }

    /**
     * 修改文件属性内容
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.PATCH)
    //@PreAuthorize("@ss.hasPermission('semi:file:modifyFile')")
    public String modifyFile(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"fullname\": \"" + fullname + "\"";
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "修改文件属性内容", reqUrlParam + ", " + reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        FileReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, FileReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 修改文件属性内容
            fileMngBusiCtrl.updateFileInfo(loginUser, fullname, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除文件
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.DELETE)
    //@PreAuthorize("@ss.hasPermission('semi:file:deleteFile')")
    public String deleteFile(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除文件", fullname);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 删除文件
            fileMngBusiCtrl.deleteFileInfo(loginUser, fullname);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }
}
