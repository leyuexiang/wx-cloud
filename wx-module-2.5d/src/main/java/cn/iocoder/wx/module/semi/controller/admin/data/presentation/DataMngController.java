package cn.iocoder.wx.module.semi.controller.admin.data.presentation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.controller.admin.data.businesscontrol.DataMngBusiCtrl;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.AuthorizeInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.HistoryInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.AuthorizeListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.DataListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.HistoryListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.util.DataMessageConst;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value="api/data")
public class DataMngController {

    private static final String FUNC_NM = "数据管理机能";

    @Autowired
    private DataMngBusiCtrl dataMngBusiCtrl;

    /**
     * 获取数据列表
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:list')")
    public String list(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取数据列表", reqUrlParam + ", " + reqBody);

        // if (StrUtil.isEmpty(reqBody)) {
        //     ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
        //     return null;
        // }

        DataReqInfo reqInfo = null;
        if (StrUtil.isNotEmpty(reqBody)) {
            try {
                reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
            } catch (Exception e) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                        ApiUtil.getApiPath(req), reqBody
                    }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 获取数据列表
            DataListResInfo dataInfoList = dataMngBusiCtrl.getDataInfoList(
                    loginUser, collection, reqInfo, current, pageSize);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dataInfoList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 添加数据
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/add", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:add')")
    public String add(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "添加数据", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (collection.equals("datasource")) {
            if (StrUtil.isEmpty(reqInfo.getName())
                    || reqInfo.getData() == null) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                        ApiUtil.getApiPath(req), reqBody, "name|data"
                    }, true);
                return null;
            }
        } else {
            if (StrUtil.isEmpty(reqInfo.getName())
                    || StrUtil.isEmpty(reqInfo.getImage())
                    || reqInfo.getData() == null) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                        ApiUtil.getApiPath(req), reqBody, "name|image|data"
                    }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 保存数据
            DataInfo dataInfo = dataMngBusiCtrl.saveDataInfo(loginUser, collection, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dataInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 获取数据
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/get", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:detail')")
    public String get(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取数据", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
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
            boolean history = false;
            if (StrUtil.isNotEmpty(reqInfo.getHistoryId()) || StrUtil.isNotEmpty(reqInfo.getTag())) {
                history = true;
            }

            // 获取数据
            Object dataInfo = dataMngBusiCtrl.getDataInfo(loginUser, collection, reqInfo, history);

            if (!history) {
                ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr((DataInfo) dataInfo));
            } else {
                ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr((HistoryInfo) dataInfo));
            }
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新数据
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:update')")
    public String update(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新数据", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataInfo.class);
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
            // 更新数据
            DataInfo dataInfo = dataMngBusiCtrl.updateDataInfo(loginUser, collection, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dataInfo));
        } catch (CmnBizException e) {
            if (e.getMsg().getCode().equals(DataMessageConst.MSG_14010003))
                // 删除文件(缩略图)
                if (StrUtil.isNotEmpty(reqInfo.getImage())) {
                    dataMngBusiCtrl.deleteFileInfo(reqInfo.getImage());
                }
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 转让数据
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/owner", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:transfer')")
    public String transfer(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "转让数据", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())
                || StrUtil.isEmpty(reqInfo.getOwnerId())
                || StrUtil.isEmpty(reqInfo.getOwnerName())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "id|ownerId|ownerName"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 转让数据
            dataMngBusiCtrl.transferDataInfo(loginUser, collection, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除数据
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:delete')")
    public String delete(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除数据", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
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
            // 删除数据
            dataMngBusiCtrl.deleteDataInfo(loginUser, collection, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 获取权限列表
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/authorize/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:authorizeList')")
    public String authorizeList(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取权限列表", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AuthorizeInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AuthorizeInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getDataId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "dataId"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 获取权限列表
            AuthorizeListResInfo authInfoList = dataMngBusiCtrl.getAuthInfoList(
                    loginUser, collection, reqInfo, current, pageSize);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(authInfoList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 数据授权
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/authorize", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:authorize')")
    public String authorize(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "数据授权", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        List<AuthorizeInfo> reqInfo = null;
        try {
            reqInfo = JsonUtil.toListObj(reqBody, AuthorizeInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        String dataId = reqInfo.get(0).getDataId();
        if (StrUtil.isEmpty(dataId)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "dataId"
                }, true);
            return null;
        }

        for (AuthorizeInfo authorizeInfo : reqInfo) {
            if (!dataId.equals(authorizeInfo.getDataId())) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010010, new Object[] {
                        ApiUtil.getApiPath(req), reqBody, "dataId"
                    }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 数据授权
            List<String> statusList = dataMngBusiCtrl.authorize(loginUser, collection, reqInfo);
            Map<String, Object> resDataMap = new HashMap<String, Object>();
            resDataMap.put("results", statusList);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resDataMap));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除权限
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/authorize/remove", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:authorizeRemove')")
    public String authorizeRemove(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除权限", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        List<AuthorizeInfo> reqInfo = null;
        try {
            reqInfo = JsonUtil.toListObj(reqBody, AuthorizeInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        String dataId = reqInfo.get(0).getDataId();
        if (StrUtil.isEmpty(dataId)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "dataId"
                }, true);
            return null;
        }

        for (AuthorizeInfo authorizeInfo : reqInfo) {
            if (!dataId.equals(authorizeInfo.getDataId())) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010010, new Object[] {
                        ApiUtil.getApiPath(req), reqBody, "dataId"
                    }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 删除权限
            List<String> statusList = dataMngBusiCtrl.deleteAuthInfo(loginUser, collection, reqInfo);
            Map<String, Object> resDataMap = new HashMap<String, Object>();
            resDataMap.put("results", statusList);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resDataMap));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 获取历史列表
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/histories", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:histories')")
    public String histories(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取历史列表", reqUrlParam + ", " + reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
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
            // 获取历史列表
            HistoryListResInfo historyInfoList = dataMngBusiCtrl.getHistoryInfoList(
                    loginUser, collection, reqInfo, current, pageSize);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(historyInfoList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新历史属性内容
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/history/update", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:updateHistory')")
    public String updateHistory(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新历史属性内容", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        HistoryInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, HistoryInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())
                || StrUtil.isEmpty(reqInfo.getDataId())
                || (StrUtil.isEmpty(reqInfo.getName())
                        && StrUtil.isEmpty(reqInfo.getRemarks())
                        && StrUtil.isEmpty(reqInfo.getTag()))) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "historyId|dataId|(name|remarks|tag)"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 更新历史属性内容
            dataMngBusiCtrl.updateHistoryInfo(loginUser, collection, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除历史
     * 
     * @param req 请求
     * @param res 响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/{collection}/history/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:data:deleteHistory')")
    public String deleteHistory(HttpServletRequest req, HttpServletResponse res,
            @PathVariable("collection") String collection) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除历史", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DataReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getDataId())
                || ListUtil.isEmptyList(reqInfo.getHistoryIdList())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "dataId|historyIds"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 删除历史
            dataMngBusiCtrl.deleteHistoryInfo(loginUser, collection, reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }
}
