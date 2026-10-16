package cn.iocoder.wx.module.iot.controller.open;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.service.IotHttpsWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/** 外部平台 HTTPS 遥测回调；匿名访问仅表示不使用后台令牌，业务层仍执行 HMAC 验签。 */
@Tag(name = "开放接口 - 物联网 HTTPS 回调")
@RestController
@RequestMapping("/open-api/iot/v1/connectors")
@RequiredArgsConstructor
public class IotHttpsWebhookController {

    private final IotHttpsWebhookService webhookService;

    @PostMapping(value = "/{connectorId}/telemetry", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "接收外部平台 HTTPS 遥测")
    @PermitAll
    public CommonResult<TwinTelemetryEvent> ingest(@PathVariable("connectorId") String connectorId,
                                                    @RequestHeader("X-Iot-Timestamp") String timestamp,
                                                    @RequestHeader("X-Iot-Signature") String signature,
                                                    @RequestBody byte[] body) {
        return success(webhookService.ingest(connectorId, timestamp, signature, body));
    }
}
