package cn.iocoder.jtyf.module.semi.controller.admin.image.presentation;

import cn.iocoder.jtyf.module.semi.common.util.*;
import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.controller.admin.file.businessinfo.FileInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.model.FileListResInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.image.businesscontrol.ImageMngBusiCtrl;
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
@RequestMapping(value="api/image")
public class ImageMngController {

    private static final String FUNC_NM = "图片管理机能";

    @Autowired
    private ImageMngBusiCtrl imageMngBusiCtrl;

    /**
     * 获取图片列表
     * 
     * @param req 请求
     * @param res 响应
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:image:list')")
    public String list(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取图片列表", reqUrlParam + ", " + reqBody);

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

        reqInfo.setType("图片");

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取图片列表
        FileListResInfo fileInfoList = imageMngBusiCtrl.getFileInfoList(
                loginUser, reqInfo, current, pageSize);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(fileInfoList));

        return null;
    }

    /**
     * 上传图片
     * 
     * @param req 请求
     * @param res 响应
     * @param file 文件
     * @param name 文件名
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
    //@PreAuthorize("@ss.hasPermission('semi:image:upload')")
    public String upload(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "file", required = true) MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
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
        reqBody.append("\"filename\" :\"" + filename + "\", ");
        reqBody.append("\"directory\" :\"" + directory + "\", ");
        reqBody.append("\"tags\" :\"" + tags + "\", ");
        reqBody.append("\"remarks\" :\"" + remarks + "\", ");
        reqBody.append("\"share\" :" + share + ", ");
        reqBody.append("\"conflict\" :\"" + conflict + "\"");
        reqBody.append("}");

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "上传图片", reqBody.toString());

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 没有指定的场合，默认共享
        if (share == null) {
            share = true;
        }

        // 文件名重命名标识
        boolean renameFileFlg = false;

        try {
            // 保存图片
            FileInfo fileInfo = imageMngBusiCtrl.saveFileInfo(loginUser,
                    file, name, filename, directory, tags, remarks, share, conflict, false);
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
            // 保存图片
            FileInfo fileInfo = imageMngBusiCtrl.saveFileInfo(loginUser,
                    file, name, filename, directory, tags, remarks, share, conflict, true);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(fileInfo));
        }

        return null;
    }

    /**
     * 获取图片
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.GET)
    //@PreAuthorize("@ss.hasPermission('semi:image:getImage')")
    public String getImage(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取图片", fullname);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        FileInfo fileInfo = null;
        try {
            // 获取图片
            fileInfo = imageMngBusiCtrl.getFileInfo(loginUser, fullname);
        } catch (CmnBizException e) {
             ApiUtil.setNotFoundResponse(req, res);
             return null;
        }

        // output
        WebUtil.outputBin(res, fileInfo.getName(), fileInfo.getContent());

        return null;
    }

    /**
     * 修改图片属性内容
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.PATCH)
    //@PreAuthorize("@ss.hasPermission('semi:image:modifyImage')")
    public String modifyImage(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"fullname\": \"" + fullname + "\"";
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "修改图片属性内容", reqUrlParam + ", " + reqBody);

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
            // 修改图片属性内容
            imageMngBusiCtrl.updateFileInfo(loginUser, fullname, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除图片
     * 
     * @param req 请求
     * @param res 响应
     * @param fullname 文件全名
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{fullname}", method=RequestMethod.DELETE)
    //@PreAuthorize("@ss.hasPermission('semi:image:deletebImage')")
    public String deletebImage(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("fullname") String fullname) throws Exception {
        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除图片", fullname);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 删除图片
            imageMngBusiCtrl.deleteFileInfo(loginUser, fullname);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }
}
