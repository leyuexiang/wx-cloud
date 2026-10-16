package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** 遥测接入编排服务，统一 MQTT、HTTPS 和模拟适配器的后续入口。 */
@Service
@RequiredArgsConstructor
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
        Instant eventTime = request.getEventTime() == null ? Instant.now() : request.getEventTime();
        boolean replay = Boolean.TRUE.equals(request.getReplay());
        String eventId = buildEventId(request, eventTime, replay);
        IotDeviceBindingDO binding = deviceBindingService.getEnabledBinding(
                request.getConnectorId(), request.getExternalDeviceId());
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
                if (throwable == null) {
                    inboxService.markPublished(event.getTenantId(), event.getEventId(), claimToken);
                } else {
                    inboxService.markFailed(event.getTenantId(), event.getEventId(), claimToken, throwable);
                }
            });
        } catch (RuntimeException ex) {
            // Kafka 发送在返回 Future 前失败时，直接记录失败并让入口返回错误。
            inboxService.markFailed(event.getTenantId(), event.getEventId(), claimToken, ex);
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
