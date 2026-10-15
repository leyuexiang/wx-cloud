package cn.iocoder.wx.module.semi.controller.admin.operation.presentation;

import cn.iocoder.wx.module.semi.common.exception.CmnBizException;
import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.DepartmentInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.businessinfo.DataInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.form.DataReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.data.presentation.model.DataListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.businesscontrol.OpnMngBusiCtrl;
import cn.iocoder.wx.module.semi.controller.admin.operation.businessinfo.RoleInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.AccountReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.form.RoleReqInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.AccountListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.RoleListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.operation.presentation.model.StatisticsResInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping(value = "api/operation")
public class OpnMngController {

    private static final String FUNC_NM = "运营管理机能";

    @Autowired
    private OpnMngBusiCtrl opnMngBusiCtrl;

    /**
     * 获取统计情报
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/statistics", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:statistics')")
    public String statistics(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取统计情报", null);

        // 获取统计情报
        StatisticsResInfo statisticsInfo = opnMngBusiCtrl.getStatisticsInfo();

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(statisticsInfo));

        return null;
    }

    /**
     * 获取用户列表
     *
     * @param req      请求
     * @param res      响应
     * @param current  当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/account/list", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:listAccount')")
    public String listAccount(HttpServletRequest req, HttpServletResponse res,
                              @RequestParam(value = "current", required = false) Integer current,
                              @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取用户列表", reqUrlParam + ", " + reqBody);

        AccountReqInfo reqInfo = null;
        if (StrUtil.isNotEmpty(reqBody)) {
            try {
                reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
            } catch (Exception e) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                        ApiUtil.getApiPath(req), reqBody
                }, true);
                return null;
            }
        }

        // 获取用户列表
        AccountListResInfo accountInfoList = opnMngBusiCtrl.getAccountInfoList(reqInfo, current, pageSize);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfoList));

        return null;
    }

    /**
     * 添加用户
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/account/add", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:addAccount')")
    public String addAccount(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "添加用户", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if ((StrUtil.isEmpty(reqInfo.getAccount())
                && StrUtil.isEmpty(reqInfo.getPhone())
                && StrUtil.isEmpty(reqInfo.getEmail()))
                || StrUtil.isEmpty(reqInfo.getPassword())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "account|phone|email|password"
            }, true);
            return null;
        }

        try {
            // rebuild
            reqInfo.rebuildAccountInfo();
            // 添加用户
            AccountInfo accountInfo = opnMngBusiCtrl.addAccountInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新用户
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/account/update", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:updateAccount')")
    public String updateAccount(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新用户", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if ((StrUtil.isEmpty(reqInfo.getId())
                && StrUtil.isEmpty(reqInfo.getAccount()))
                || !reqInfo.checkRequiredUpdateFieldsNotEmpty()) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id|account|roles|departmentId|teams"
            }, true);
            return null;
        }

        try {
            // 更新用户
            AccountInfo accountInfo = opnMngBusiCtrl.updateAccountInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(accountInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 禁用用户
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/account/disable", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:disableAccount')")
    public String disableAccount(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "禁用用户", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        AccountReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, AccountReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId()) && StrUtil.isEmpty(reqInfo.getAccount())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id|account"
            }, true);
            return null;
        }

        try {
            // 禁用用户
            opnMngBusiCtrl.disableAccountInfo(reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 获取角色列表
     *
     * @param req      请求
     * @param res      响应
     * @param current  当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/roles", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:listRole')")
    public String listRole(HttpServletRequest req, HttpServletResponse res,
                           @RequestParam(value = "current", required = false) Integer current,
                           @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取角色列表", reqUrlParam + ", " + reqBody);

        RoleReqInfo reqInfo = null;
        if (StrUtil.isNotEmpty(reqBody)) {
            try {
                reqInfo = JsonUtil.toObj(reqBody, RoleReqInfo.class);
            } catch (Exception e) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                        ApiUtil.getApiPath(req), reqBody
                }, true);
                return null;
            }
        }

        // 获取角色列表
        RoleListResInfo roleInfoList = opnMngBusiCtrl.getRoleInfoList(reqInfo, current, pageSize);

        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(roleInfoList));

        return null;
    }

    /**
     * 创建角色
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/role/create", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:createRole')")
    public String createRole(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "创建角色", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        RoleInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, RoleInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getName())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "name"
            }, true);
            return null;
        }

        try {
            // 创建角色
            RoleInfo roleInfo = opnMngBusiCtrl.addRoleInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(roleInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新角色
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/role/update", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:updateRole')")
    public String updateRole(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新角色", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        RoleInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, RoleInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())
                && (StrUtil.isEmpty(reqInfo.getName()) || reqInfo.getAuthCd() == null)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id|name|authCd"
            }, true);
            return null;
        }

        try {
            // 更新角色
            RoleInfo roleInfo = opnMngBusiCtrl.updateRoleInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(roleInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除角色
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/role/delete", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:deleteRole')")
    public String deleteRole(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除角色", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        RoleInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, RoleInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id"
            }, true);
            return null;
        }

        try {
            // 删除角色
            opnMngBusiCtrl.deleteRoleInfo(reqInfo);
            ApiUtil.setEmptyJsonObjectResponse(res);
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 创建部门
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/department/create", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:createDepartment')")
    public String createDepartment(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "创建部门", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DepartmentInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DepartmentInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getName())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "name"
            }, true);
            return null;
        }

        try {
            // 创建部门
            DepartmentInfo departmentInfo = opnMngBusiCtrl.addDepartmentInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(departmentInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新部门
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/department/update", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:updateDepartment')")
    public String updateDepartment(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "更新部门", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DepartmentInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DepartmentInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        try {
            // 更新部门
            DepartmentInfo departmentInfo = opnMngBusiCtrl.updateDepartmentInfo(reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(departmentInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 删除部门
     *
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/department/delete", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:deleteDepartment')")
    public String deleteDepartment(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "删除部门", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        DepartmentInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, DepartmentInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id"
            }, true);
            return null;
        }

        // 删除部门
        opnMngBusiCtrl.deleteDepartmentInfo(reqInfo);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 获取数据列表
     *
     * @param req        请求
     * @param res        响应
     * @param collection 数据分类
     * @param current    当前页
     * @param pageSize   每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/data/{collection}/list", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:listData')")
    public String listData(HttpServletRequest req, HttpServletResponse res,
                           @PathVariable("collection") String collection,
                           @RequestParam(value = "current", required = false) Integer current,
                           @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        // call API log
        ApiUtil.callApiLog(req, FUNC_NM, "获取数据列表", reqBody);

        // if (StrUtil.isEmpty(reqBody)) {
        //     ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
        //     return null;
        // }

        DataReqInfo reqInfo = null;
        if (StrUtil.isNotEmpty(reqBody)) {
            try {
                reqInfo = JsonUtil.toObj(reqBody, DataReqInfo.class);
            } catch (Exception e) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                        ApiUtil.getApiPath(req), reqBody
                }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 获取数据列表
            DataListResInfo dataInfoList = opnMngBusiCtrl.getDataInfoList(
                    loginUser, collection, reqInfo, current, pageSize);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dataInfoList));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }

    /**
     * 更新数据
     *
     * @param req        请求
     * @param res        响应
     * @param collection 数据分类
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value = "/data/{collection}/update", method = RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:opnmng:updateData')")
    public String updateData(HttpServletRequest req, HttpServletResponse res,
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
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[]{
                    ApiUtil.getApiPath(req), reqBody
            }, true);
            return null;
        }

        if (StrUtil.isEmpty(reqInfo.getId())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[]{
                    ApiUtil.getApiPath(req), reqBody, "id"
            }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        try {
            // 更新数据
            DataInfo dataInfo = opnMngBusiCtrl.updateDataInfo(
                    loginUser, collection, reqInfo);
            ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(dataInfo));
        } catch (CmnBizException e) {
            ApiUtil.setErrorJsonResponse(res, e.getMsg().getMsgContent());
        }

        return null;
    }
}
