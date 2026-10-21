package cn.iocoder.wx.module.iot.controller;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.service.IotTelemetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物联网统一接入入口。
 *
 * <p>当前接口用于联调和回归；真实 MQTT、HTTPS 适配器接入后复用同一个服务方法。</p>
 */
@Tag(name = "管理后台 - 物联网遥测")
@RestController
@RequestMapping("/admin-api/iot/telemetry")
@Validated
@RequiredArgsConstructor
public class IotTelemetryController {

    private final IotTelemetryService telemetryService;

    @PostMapping("/mock")
    @Operation(summary = "接收模拟遥测并发布统一事件")
    @PreAuthorize("@ss.hasPermission('iot:telemetry:mock')")
    public CommonResult<TwinTelemetryEvent> ingestMock(@Valid @RequestBody TwinTelemetryIngestRequest request) {
        return CommonResult.success(telemetryService.ingest(request));
    }
}
