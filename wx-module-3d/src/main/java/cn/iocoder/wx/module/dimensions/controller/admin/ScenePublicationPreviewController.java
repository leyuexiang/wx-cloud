package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationTicketService;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationFileService;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 票据只授权固定发布的只读预览，不取得后台登录身份。 */
@RestController
@RequestMapping("/dim3d/scene/publication")
public class ScenePublicationPreviewController {
    @Resource private ScenePublicationTicketService tickets;
    @Resource private ScenePublicationFileService files;

    /** 使用专用头避免票据进入地址及参数日志；四包响应和失败结果均禁止缓存。 */
    @PostMapping("/preview")
    @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, Object>> preview(
            @RequestHeader(value = "X-Scene-Ticket", required = false) String ticket, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(tickets.consumeSnapshot(ticket));
    }

    /** 资源列表仅含固定消费者字段，不公开内部快照与存储前缀。 */
    @PostMapping("/preview/resources")
    @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, Object>> resources(
            @RequestHeader(value = "X-Scene-Ticket", required = false) String ticket, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(files.list(ticket));
    }

    /** 文件请求不能覆盖票据绑定的发布、租户和资源版本。 */
    @PostMapping("/preview/file")
    @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, String>> file(
            @RequestHeader(value = "X-Scene-Ticket", required = false) String ticket,
            @RequestBody(required = false) JsonNode body, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (body == null || !body.isObject() || body.size() != 2 || !body.path("resourceKey").isTextual() || !body.path("path").isTextual())
            throw new ServiceException(400, "文件请求仅允许资源键和清单路径");
        return CommonResult.success(files.file(ticket, body.get("resourceKey").textValue(), body.get("path").textValue()));
    }
}
