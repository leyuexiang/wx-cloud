package cn.iocoder.wx.module.iot.controller.admin.messageinbox;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxRespVO;
import cn.iocoder.wx.module.iot.service.IotMessageInboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/** 物联网接入事件监控和失败重放接口。 */
@Tag(name = "管理后台 - 物联网接入事件")
@RestController
@RequestMapping("/iot/message-inbox")
@Validated
@RequiredArgsConstructor
public class IotMessageInboxController {

    private final IotMessageInboxService inboxService;

    @GetMapping("/page")
    @Operation(summary = "分页查询物联网接入事件")
    @PreAuthorize("@ss.hasPermission('iot:message-inbox:query')")
    public CommonResult<PageResult<IotMessageInboxRespVO>> page(@Valid IotMessageInboxPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(inboxService.getPage(pageReqVO), IotMessageInboxRespVO.class));
    }

    @PostMapping("/replay")
    @Operation(summary = "重放失败的物联网接入事件")
    @Parameter(name = "eventId", description = "事件编号", required = true)
    @PreAuthorize("@ss.hasPermission('iot:message-inbox:replay')")
    public CommonResult<Boolean> replay(@NotBlank(message = "事件编号不能为空") @RequestParam("eventId") String eventId) {
        inboxService.replay(eventId);
        return success(true);
    }
}
