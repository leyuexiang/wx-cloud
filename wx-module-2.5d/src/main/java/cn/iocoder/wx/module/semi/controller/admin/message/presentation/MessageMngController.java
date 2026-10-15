package cn.iocoder.wx.module.semi.controller.admin.message.presentation;

import java.util.HashMap;
import java.util.Map;

import cn.iocoder.wx.module.semi.common.util.*;
import cn.iocoder.wx.module.semi.controller.admin.account.businessinfo.AccountInfo;
import cn.iocoder.wx.module.semi.controller.admin.message.businesscontrol.MessageMngBusiCtrl;
import cn.iocoder.wx.module.semi.controller.admin.message.presentation.form.MessageListResInfo;
import cn.iocoder.wx.module.semi.controller.admin.message.presentation.form.MessageReqInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value="api/message")
public class MessageMngController {

    private static final  String FUNC_NM = "消息管理机能";

    @Autowired
    private MessageMngBusiCtrl messageMngBusiCtrl;

    /**
     * 统计未读消息总数
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/unread/count")
    //@PreAuthorize("@ss.hasPermission('semi:message:countUnread')")
    public String countUnread(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "统计未读消息总数", null);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 统计未读消息总数
        int count = messageMngBusiCtrl.getUnreadMessageCount(loginUser);
        Map<String, Integer> resDataMap = new HashMap<String, Integer>();
        resDataMap.put("unread", count);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(resDataMap));

        return null;
    }

    /**
     * 获取消息列表
     * 
     * @param req 请求
     * @param res 响应
     * @param current 当前页
     * @param pageSize 每页记录数
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/list", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:message:list')")
    public String list(HttpServletRequest req, HttpServletResponse res,
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) throws Exception {
        // 读取请求URL参数
        String reqUrlParam = "\"current\": " + current + ", \"pageSize\": " + pageSize;
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "获取消息列表", reqUrlParam + ", " + reqBody);

        MessageReqInfo reqInfo = null;
        if (StrUtil.isNotEmpty(reqBody)) {
            try {
                reqInfo = JsonUtil.toObj(reqBody, MessageReqInfo.class);
            } catch (Exception e) {
                ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                        ApiUtil.getApiPath(req), reqBody
                    }, true);
                return null;
            }
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 获取消息列表
        MessageListResInfo messageInfoList = messageMngBusiCtrl.getMessageInfoList(
                loginUser, reqInfo, current, pageSize);
        ApiUtil.setJsonResponse(res, JsonUtil.toJsonStr(messageInfoList));

        return null;
    }

    /**
     * 设置消息阅读状态
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/read", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:message:read')")
    public String read(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "设置消息阅读状态", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        MessageReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, MessageReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo.getIds())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "ids"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 设置消息阅读状态
        messageMngBusiCtrl.registerMessageRead(loginUser, reqInfo);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 设置全部消息已读
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/read/all", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:message:readAll')")
    public String readAll(HttpServletRequest req, HttpServletResponse res) throws Exception {
        ApiUtil.callApiLog(req, FUNC_NM, "设置全部消息已读", null);

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 设置全部消息已读
        messageMngBusiCtrl.registerAllMessageRead(loginUser);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }

    /**
     * 删除消息
     * 
     * @param req 请求
     * @param res 响应
     * @return
     * @throws Exception 异常
     */
    @RequestMapping(value="/delete", method=RequestMethod.POST)
    //@PreAuthorize("@ss.hasPermission('semi:message:delete')")
    public String delete(HttpServletRequest req, HttpServletResponse res) throws Exception {
        // 读取请求Body内容
        String reqBody = ApiUtil.readTxtBody(req);

        ApiUtil.callApiLog(req, FUNC_NM, "删除消息", reqBody);

        if (StrUtil.isEmpty(reqBody)) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010006, null, false);
            return null;
        }

        MessageReqInfo reqInfo = null;
        try {
            reqInfo = JsonUtil.toObj(reqBody, MessageReqInfo.class);
        } catch (Exception e) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010007, new Object[] {
                    ApiUtil.getApiPath(req), reqBody
                }, true);
            return null;
        }

        if (ListUtil.isEmptyList(reqInfo.getIds())) {
            ApiUtil.setErrorJsonResponse(res, ApiMessageConst.MSG_10010008, new Object[] {
                    ApiUtil.getApiPath(req), reqBody, "ids"
                }, true);
            return null;
        }

        // 取得登录用户详细情报
        AccountInfo loginUser = WebUtil.getLoginUser(req);

        // 删除消息
        messageMngBusiCtrl.deleteMessageInfo(loginUser, reqInfo);
        ApiUtil.setEmptyJsonObjectResponse(res);

        return null;
    }
}
