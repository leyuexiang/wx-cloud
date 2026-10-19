package cn.iocoder.wx.module.iot.api;

import lombok.Builder;
import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数字孪生遥测事件的跨服务契约。
 *
 * <p>连接器只负责协议接入和字段映射，规则引擎、三维服务等下游只依赖此模型。</p>
 */
@Data
@Builder
// 标准 Kafka 反序列化器需要无参构造，避免下游依赖 Web 层的构造参数模块。
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class TwinTelemetryEvent {

    /** 事件唯一标识，用于下游幂等。 */
    private String eventId;
    /** 事件契约版本。 */
    private String schemaVersion;
    private Instant eventTime;
    private Long tenantId;
    private String sceneId;
    private String objectId;
    private String deviceId;
    private String connectorId;
    private String externalDeviceId;
    private String pointCode;
    private Object value;
    private IotValueType valueType;
    private String unit;
    /** 质量码，例如 GOOD、BAD、UNCERTAIN。 */
    private String quality;
    /** 点位映射配置版本。 */
    private String mappingRevision;
    /** 是否由历史补发或重放产生。 */
    private Boolean replay;
    /** 跨服务链路追踪标识。 */
    private String traceId;
}
