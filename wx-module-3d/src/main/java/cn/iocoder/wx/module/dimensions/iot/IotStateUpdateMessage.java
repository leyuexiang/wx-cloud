package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import lombok.Builder;
import lombok.Data;

/** 面向三维运行时的标准状态更新，避免把连接器凭据等接入字段推送到浏览器。 */
@Data
@Builder
public class IotStateUpdateMessage {
    private String subscriptionId;
    private String sceneId;
    private String objectId;
    private String eventId;
    private String schemaVersion;
    private String eventTime;
    private Long tenantId;
    private String deviceId;
    private String pointCode;
    private Object value;
    private String valueType;
    private String unit;
    private String quality;
    private String stateVersion;

    static IotStateUpdateMessage from(String subscriptionId, String sceneId, String objectId,
                                      TwinTelemetryEvent event, String stateVersion) {
        return IotStateUpdateMessage.builder()
                .subscriptionId(subscriptionId).sceneId(sceneId).objectId(objectId)
                .eventId(event.getEventId()).schemaVersion(event.getSchemaVersion())
                .eventTime(event.getEventTime() == null ? null : event.getEventTime().toString())
                .tenantId(event.getTenantId()).deviceId(event.getDeviceId()).pointCode(event.getPointCode())
                .value(event.getValue()).valueType(event.getValueType() == null ? null : event.getValueType().name())
                .unit(event.getUnit()).quality(event.getQuality()).stateVersion(stateVersion)
                .build();
    }
}
