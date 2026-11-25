package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.wx.module.dimensions.iot.IotStateQueryService;
import cn.iocoder.wx.module.dimensions.controller.admin.vo.IotStateRespVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 点位当前态查询；身份与权限在服务层显式校验，不受旧模块 permitAll 配置影响。 */
@RestController
@RequestMapping("/dimensions/iot-state")
@Tag(name = "管理后台 - 物联网点位状态")
@Validated
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateController {
    private final IotStateQueryService queryService;

    @GetMapping("/get")
    @Operation(summary = "查询当前租户设备点位最新状态，未接收或已过期时返回空值")
    public CommonResult<IotStateRespVO> get(
            @RequestParam("deviceId") @NotBlank @Size(max = 256) String deviceId,
            @RequestParam("pointCode") @NotBlank @Size(max = 256) String pointCode,
            @RequestParam(value = "sceneId", required = false) @Size(max = 19) String sceneId,
            @RequestParam(value = "objectId", required = false) @Size(max = 256) String objectId,
            @RequestParam(value = "domain", required = false) @Size(max = 6) String domain) {
        // 运行态携带场景和实例，兼容原有租户级状态查询。
        return CommonResult.success(IotStateRespVO.from(queryService.get(deviceId, pointCode, sceneId, objectId, domain)));
    }

    /** 票据放在专用请求头，发布页的最新测点状态不得进入共享缓存。 */
    @GetMapping("/publication")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    @Operation(summary = "查询指定发布修订已绑定的设备点位最新状态")
    public CommonResult<IotStateRespVO> publication(
            @RequestParam("publicationId") @NotBlank @Size(max = 23) String publicationId,
            @RequestHeader(value = "X-Scene-Ticket", required = false) String ticket,
            @RequestParam("objectId") @NotBlank @Size(max = 256) String objectId,
            @RequestParam("deviceId") @NotBlank @Size(max = 256) String deviceId,
            @RequestParam("pointCode") @NotBlank @Size(max = 256) String pointCode,
            @RequestParam(value = "domain", required = false) @Size(max = 6) String domain,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(IotStateRespVO.from(queryService.getPublished(publicationId, ticket, objectId, deviceId, pointCode, domain)));
    }
}
