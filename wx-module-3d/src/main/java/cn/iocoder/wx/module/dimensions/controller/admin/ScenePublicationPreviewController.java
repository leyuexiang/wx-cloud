package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationTicketService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 票据只授权固定发布的只读预览，不取得后台登录身份。 */
@RestController
@RequestMapping("/dim3d/scene/publication")
public class ScenePublicationPreviewController {
    @Resource private ScenePublicationTicketService tickets;

    /** 使用专用头避免票据进入地址及参数日志；四包响应和失败结果均禁止缓存。 */
    @PostMapping("/preview")
    @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, Object>> preview(
            @RequestHeader(value = "X-Scene-Ticket", required = false) String ticket, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(tickets.consumeSnapshot(ticket));
    }
}
