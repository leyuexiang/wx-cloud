package cn.iocoder.wx.module.dimensions.controller.admin.vo;

import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import io.swagger.v3.oas.annotations.media.Schema;

/** 状态查询契约；时间采用 ISO 字符串，保留纳秒且不受 Web 全局毫秒配置影响。 */
@Schema(description = "管理后台 - 物联网点位最新状态")
public record IotStateRespVO(String eventId, String schemaVersion, String eventTime, Long tenantId,
                             String deviceId, String pointCode, Object value, IotValueType valueType,
                             String unit, String quality, String sceneId, String objectId,
                             String mappingRevision, Boolean replay, String traceId) {
    public static IotStateRespVO from(TwinTelemetryEvent event) {
        return event == null ? null : new IotStateRespVO(event.getEventId(), event.getSchemaVersion(),
                event.getEventTime().toString(), event.getTenantId(), event.getDeviceId(), event.getPointCode(),
                event.getValue(), event.getValueType(), event.getUnit(), event.getQuality(), event.getSceneId(),
                event.getObjectId(), event.getMappingRevision(), event.getReplay(), event.getTraceId());
    }
}
