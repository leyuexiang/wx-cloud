package cn.iocoder.jtyf.module.semi.controller.admin.files.presentation;

import java.util.ArrayList;
import java.util.List;

import cn.iocoder.jtyf.module.semi.common.util.*;
import cn.iocoder.jtyf.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.jtyf.module.semi.common.exception.CmnBizException;
import cn.iocoder.jtyf.module.semi.controller.admin.file.presentation.form.FileReqInfo;
import cn.iocoder.jtyf.module.semi.controller.admin.files.businesscontrol.FilesMngBusiCtrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value="api/files")
public class FilesMngController {

    private static final String FUNC_NM = "文件批量管理机能";

    @Autowired
    private FilesMngBusiCtrl filesMngBusiCtrl;

    /**
     * 批量修改文件属性内容
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:files:update')")
    public String update(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "批量修改文件属性内容", reqBody);

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

        if (ListUtil.isEmptyList(reqInfo.getFullnameList())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullnames"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 批量修改文件属性内容
            filesMngBusiCtrl.updateFileInfoList(loginUser, reqInfo.getFullnameList(), reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 批量删除文件
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:files:delete')")
    public String delete(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "批量删除文件", reqBody);

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

        if (ListUtil.isEmptyList(reqInfo.getFullnameList())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullnames"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 批量删除文件
            List<String> fullnameList = filesMngBusiCtrl.deleteFileInfoList(
                    loginUser, reqInfo.getFullnameList(), reqInfo.isPhysically());

            List<String[]> jsonItemList = new ArrayList<String[]>();
            String[] kvItem = new String[2];
            if (reqInfo.isPhysically()) {
                kvItem[0] = "deleted";
            } else {
                kvItem[0] = "recycled";
            }
            kvItem[1] = "[" + StrUtil.join(fullnameList, ",", "\"") + "]";
            jsonItemList.add(kvItem);

            ApiUtil.setJsonResponse(res, ApiUtil.buildJson(jsonItemList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 批量恢复文件
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/restore", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:files:restore')")
    public String restore(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "批量恢复文件", reqBody);

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

        if (ListUtil.isEmptyList(reqInfo.getFullnameList())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "fullnames"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 批量恢复文件
            List<String> restoredfullnameList = filesMngBusiCtrl.restoreFileInfoList(
                    loginUser, reqInfo.getFullnameList());

            List<String[]> jsonItemList = new ArrayList<String[]>();
            String[] kvItem = new String[2];
            kvItem[0] = "restored";
            kvItem[1] = "[" + StrUtil.join(restoredfullnameList, ",", "\"") + "]";
            jsonItemList.add(kvItem);

            ApiUtil.setJsonResponse(res, ApiUtil.buildJson(jsonItemList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }
}
