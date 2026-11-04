package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** 遥测接入编排服务，统一 MQTT、HTTPS 和模拟适配器的后续入口。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IotTelemetryService {

    private final IotEventPublisher eventPublisher;
    private final IotDeviceBindingService deviceBindingService;
    private final IotPointMappingService pointMappingService;
    private final IotMessageInboxService inboxService;

    /**
     * 构造统一事件并发布。
     *
     * <p>事件 ID 使用输入内容生成；外部适配器应尽量传入事件时间和链路标识，便于重试时保持幂等。</p>
     */
    public TwinTelemetryEvent ingest(TwinTelemetryIngestRequest request) {
        return ingest(request, false);
    }

    /** MQTT 来源必须已登记启用绑定，避免外部报文兜底生成未经管理的内部设备身份。 */
    public TwinTelemetryEvent ingestBound(TwinTelemetryIngestRequest request) {
        return ingest(request, true);
    }

    private TwinTelemetryEvent ingest(TwinTelemetryIngestRequest request, boolean bindingRequired) {
        Instant eventTime = request.getEventTime() == null ? Instant.now() : request.getEventTime();
        boolean replay = Boolean.TRUE.equals(request.getReplay());
        String eventId = buildEventId(request, eventTime, replay);
        IotDeviceBindingDO binding = deviceBindingService.getEnabledBinding(
                request.getConnectorId(), request.getExternalDeviceId());
        if (bindingRequired && binding == null) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.DEVICE_BINDING_NOT_EXISTS);
        }
        IotMappedPoint mappedPoint = pointMappingService.map(binding, request.getPointCode(),
                request.getValue(), request.getValueType(), request.getUnit());
        Long tenantId = request.getTenantId() == null
                ? TenantContextHolder.getTenantId() : request.getTenantId();
        TwinTelemetryEvent event = TwinTelemetryEvent.builder()
                .eventId(eventId)
                .schemaVersion("1.0")
                .eventTime(eventTime)
                .tenantId(tenantId)
                .sceneId(preferBinding(binding == null ? null : binding.getSceneId(), request.getSceneId()))
                .objectId(preferBinding(binding == null ? null : binding.getObjectId(), request.getObjectId()))
                .deviceId(preferBinding(binding == null ? null : binding.getDeviceId(), request.getDeviceId()))
                .connectorId(request.getConnectorId())
                .externalDeviceId(request.getExternalDeviceId())
                .pointCode(mappedPoint.pointCode())
                .value(mappedPoint.value())
                .valueType(mappedPoint.valueType())
                .unit(mappedPoint.unit())
                .quality(request.getQuality())
                .mappingRevision(preferBinding(binding == null ? null : binding.getMappingRevision(),
                        request.getMappingRevision()))
                .replay(replay)
                .traceId(request.getTraceId() == null ? UUID.randomUUID().toString() : request.getTraceId())
                .build();
        String claimToken = inboxService.tryClaim(event);
        if (claimToken == null) {
            return event;
        }
        try {
            eventPublisher.publish(event).whenComplete((result, throwable) -> {
                try {
                    if (throwable == null) {
                        inboxService.markPublished(event.getTenantId(), event.getEventId(), claimToken);
                    } else {
                        inboxService.markFailed(event.getTenantId(), event.getEventId(), claimToken, throwable);
                    }
                } catch (RuntimeException recordingError) {
                    // HTTP 可能已返回或 MQTT 已确认，回调异常必须落日志；租户以原事件为准。
                    // 不重发、不改写发送结果，保留现有 CLAIMED 超时抢占及下游幂等边界。
                    log.error("物联网异步投递状态更新失败，tenantId={}, eventId={}, publishSucceeded={}",
                            event.getTenantId(), event.getEventId(), throwable == null, recordingError);
                }
            });
        } catch (RuntimeException ex) {
            // 同步发送失败时尝试记录状态；数据库故障不能覆盖原始 Kafka 错误。
            try {
                inboxService.markFailed(event.getTenantId(), event.getEventId(), claimToken, ex);
            } catch (RuntimeException recordingError) {
                // 保留两个故障供调用方排查，并防止同一异常对象自我附加。
                if (recordingError != ex) {
                    ex.addSuppressed(recordingError);
                }
            }
            throw ex;
        }
        return event;
    }

    private String buildEventId(TwinTelemetryIngestRequest request, Instant eventTime, boolean replay) {
        String source = String.join("|",
                safe(request.getConnectorId()),
                safe(request.getExternalDeviceId()),
                safe(request.getPointCode()),
                eventTime.toString(),
                String.valueOf(request.getValue()),
                safe(request.getTraceId()),
                String.valueOf(replay));
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** 绑定配置是主数据，接入请求中的同名字段仅作为未配置时的兼容兜底。 */
    private String preferBinding(String bindingValue, String requestValue) {
        return bindingValue == null || bindingValue.isBlank() ? requestValue : bindingValue;
    }
}
