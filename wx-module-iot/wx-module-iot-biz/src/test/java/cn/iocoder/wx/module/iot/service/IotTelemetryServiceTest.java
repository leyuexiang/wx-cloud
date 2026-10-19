package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 遥测编排行为测试：验证幂等投递、绑定主数据和异步状态边界。 */
class IotTelemetryServiceTest {
    private final IotEventPublisher publisher = mock(IotEventPublisher.class);
    private final IotDeviceBindingService bindings = mock(IotDeviceBindingService.class);
    private final IotMessageInboxService inbox = mock(IotMessageInboxService.class);
    private final IotTelemetryService service = new IotTelemetryService(publisher, bindings,
            new IotPointMappingService(new ObjectMapper()), inbox);

    /** 使用固定租户与抢占令牌，确保异步状态更新携带正确上下文。 */
    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        when(inbox.tryClaim(any())).thenReturn("claim-1");
    }

    /** 释放线程上下文，避免用例之间租户泄漏。 */
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 发送结果完成前不能标记成功；完成后必须使用原始租户和抢占令牌写回。 */
    @Test
    void shouldWaitForBrokerAcknowledgement() {
        CompletableFuture<SendResult<String, Object>> pending = new CompletableFuture<>();
        when(publisher.publish(any())).thenReturn(pending);
        TwinTelemetryEvent event = service.ingest(request());
        verify(inbox, never()).markPublished(any(), anyString(), anyString());
        TenantContextHolder.setTenantId(8L);
        pending.complete(null);
        verify(inbox).markPublished(7L, event.getEventId(), "claim-1");
        assertEquals(8L, TenantContextHolder.getTenantId());
    }

    /** 未取得抢占令牌时不重复发布；相同稳定输入始终获得相同事件编号。 */
    @Test
    void shouldSkipDuplicatePublishWithStableEventId() {
        when(inbox.tryClaim(any())).thenReturn(null);
        TwinTelemetryEvent first = service.ingest(request());
        TwinTelemetryEvent second = service.ingest(request());
        assertEquals(first.getEventId(), second.getEventId());
        verifyNoInteractions(publisher);
    }

    /** 绑定主数据覆盖请求中的临时字段，并应用绑定上的点位映射。 */
    @Test
    void shouldPreferBindingAndMapPoint() {
        IotDeviceBindingDO binding = new IotDeviceBindingDO();
        binding.setDeviceId("bound-device");
        binding.setSceneId("bound-scene");
        binding.setObjectId("bound-object");
        binding.setMappingRevision("v2");
        binding.setPointMappingJson("{\"points\":{\"temp\":{\"pointCode\":\"temperature\",\"scale\":2}}}");
        when(bindings.getEnabledBinding("connector-1", "external-1")).thenReturn(binding);
        when(inbox.tryClaim(any())).thenReturn(null);
        TwinTelemetryEvent event = service.ingest(request());
        assertEquals("bound-device", event.getDeviceId());
        assertEquals("bound-scene", event.getSceneId());
        assertEquals("bound-object", event.getObjectId());
        assertEquals("v2", event.getMappingRevision());
        assertEquals("temperature", event.getPointCode());
        assertEquals(0, new BigDecimal("20").compareTo((BigDecimal) event.getValue()));
    }

    /** 异步发送异常只记录失败，不得同时标记发布成功。 */
    @Test
    void shouldRecordAsyncPublishFailure() {
        RuntimeException error = new IllegalStateException("broker unavailable");
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(error));
        TwinTelemetryEvent event = service.ingest(request());
        verify(inbox).markFailed(7L, event.getEventId(), "claim-1", error);
        verify(inbox, never()).markPublished(any(), anyString(), anyString());
    }

    /** 同步抛错时立即记录失败并将同一异常返回调用方。 */
    @Test
    void shouldPropagateSynchronousPublishFailure() {
        RuntimeException error = new IllegalStateException("broker unavailable");
        when(publisher.publish(any())).thenThrow(error);
        assertSame(error, assertThrows(RuntimeException.class, () -> service.ingest(request())));
        verify(inbox).markFailed(eq(7L), anyString(), eq("claim-1"), same(error));
    }

    /** 失败记录也出错时保留发送异常为主异常，数据库异常作为附加异常暴露。 */
    @Test
    void shouldPreservePublishErrorWhenFailureRecordingThrows() {
        RuntimeException error = new IllegalStateException("broker unavailable");
        RuntimeException databaseError = new IllegalStateException("database unavailable");
        when(publisher.publish(any())).thenThrow(error);
        doThrow(databaseError).when(inbox).markFailed(any(), anyString(), anyString(), any());
        RuntimeException actual = assertThrows(RuntimeException.class, () -> service.ingest(request()));
        assertSame(error, actual);
        assertArrayEquals(new Throwable[]{databaseError}, actual.getSuppressed());
    }

    /** MQTT 专用入口要求绑定存在，并且只查询一次绑定，避免适配层重复查询。 */
    @Test
    void shouldRequireBindingForMqttWithoutWritingInbox() {
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class,
                () -> service.ingestBound(request()));
        verify(bindings).getEnabledBinding("connector-1", "external-1");
        verifyNoInteractions(inbox, publisher);
    }

    /** 标准报文固定事件时间与追踪号，模拟外部平台重试相同遥测。 */
    private TwinTelemetryIngestRequest request() {
        TwinTelemetryIngestRequest request = new TwinTelemetryIngestRequest();
        request.setConnectorId("connector-1");
        request.setExternalDeviceId("external-1");
        request.setPointCode("temp");
        request.setValue(10);
        request.setValueType(IotValueType.NUMBER);
        request.setEventTime(Instant.parse("2026-10-17T12:00:00Z"));
        request.setTraceId("trace-1");
        request.setDeviceId("request-device");
        request.setSceneId("request-scene");
        request.setObjectId("request-object");
        request.setMappingRevision("v1");
        return request;
    }
}
