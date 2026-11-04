package cn.iocoder.wx.module.iot.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/** 模拟接入和后续 MQTT、HTTPS 适配器共用的标准化输入。 */
@Data
public class TwinTelemetryIngestRequest {

    @NotBlank
    private String connectorId;
    @NotBlank
    private String externalDeviceId;
    @NotBlank
    private String pointCode;
    @NotNull
    private Object value;
    @NotNull
    private IotValueType valueType;
    private Long tenantId;
    private String sceneId;
    private String objectId;
    private String deviceId;
    private String unit;
    private String quality;
    private String mappingRevision;
    private Instant eventTime;
    private Boolean replay;
    private String traceId;
}
