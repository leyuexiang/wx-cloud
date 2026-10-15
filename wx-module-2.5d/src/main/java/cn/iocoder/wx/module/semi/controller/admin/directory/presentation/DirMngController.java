package cn.iocoder.wx.module.semi.controller.admin.directory.presentation;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.directory.businesscontrol.DirMngBusiCtrl;
import cn.iocoder.wx.module.semi.controller.admin.directory.businessinfo.DirInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.form.DirReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.model.DirListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.directory.presentation.model.DirWithFileListResInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value="api/directory")
public class DirMngController {

    private static final String FUNC_NM = "文件夹管理机能";

    @Autowired
    private DirMngBusiCtrl dirMngBusiCtrl;

    /**
     * 获取文件夹列表
     * 
     * @param req 请求
     * @param res 响应
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:dirmng:list')")
    public String list(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取文件夹列表", reqUrlParam + ", " + reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DirReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DirReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getFullpath())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullpath"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取文件夹列表
        DirListResInfo dirInfoList = dirMngBusiCtrl.getDirInfoList(loginUser.getId(),
                reqInfo, current, pageSize);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dirInfoList));

        return null;
    }

    /**
     * 添加文件夹
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/add", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:dirmng:add')")
    public String add(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "添加文件夹", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DirReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DirReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getFullpath())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullpath"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 添加文件夹
            DirInfo dirInfo = dirMngBusiCtrl.addDirInfo(loginUser.getId(), reqInfo.getFullpath());
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dirInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新文件夹
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:dirmng:update')")
    public String update(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新文件夹", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DirReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DirReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getOldFullpath())
                || StrUtil.isEmpty(reqInfo.getNewFullpath())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "oldFullpath|newFullpath"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 更新文件夹
            dirMngBusiCtrl.updateDirInfo(loginUser.getId(),
                    reqInfo.getOldFullpath(), reqInfo.getNewFullpath());
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除文件夹
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:dirmng:delete')")
    public String delete(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除文件夹", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DirReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DirReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo.getFullpaths())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullpaths"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 删除文件夹
        DirWithFileListResInfo deletedDirAndFileList = dirMngBusiCtrl.deleteDirInfo(
                loginUser.getId(), reqInfo.getFullpaths(), reqInfo.isPhysically());
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(deletedDirAndFileList));

        return null;
    }

    /**
     * 恢复文件夹
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/restore", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:dirmng:restore')")
    public String restore(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "恢复文件夹", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DirReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DirReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo.getFullpaths())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullpaths"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 恢复文件夹
        DirWithFileListResInfo restoredDirAndFileList = dirMngBusiCtrl.restoreDirInfo(
                loginUser.getId(), reqInfo.getFullpaths());
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(restoredDirAndFileList));

        return null;
    }
}
