package cn.iocoder.wx.module.semi.common.presentation;

import cn.iocoder.wx.module.semi.common.msg.MessageConst;
import cn.iocoder.wx.module.semi.common.msg.MsgResourceManager;
import cn.iocoder.wx.module.semi.common.util.ApiUtil;
import cn.iocoder.wx.module.semi.common.util.StrUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class GlobalController {

    @RequestMapping(value="/error.json")
    public String error(HttpServletRequest req, HttpServletResponse res) throws Exception {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String errorMsg = (String) req.getAttribute(GlobalErrorAdvice.ERROR_MSG);
        errorMsg = StrUtil.isEmpty(errorMsg)
                ? MsgResourceManager.getMessage(MessageConst.MSG_01990001)
                : errorMsg;
        String detailMsg = (String) req.getAttribute(GlobalErrorAdvice.DETAIL_MSG);
        detailMsg = StrUtil.isEmpty(detailMsg)
                ? ""
                : detailMsg;
        if(cn.hutool.core.util.StrUtil.isNotEmpty(detailMsg) && detailMsg.equals("Access Denied")){
            errorMsg = "Access Denied";
        }
        ApiUtil.setDetailErrorJsonResponse(res, errorMsg, detailMsg);
        return null;
    }
}
