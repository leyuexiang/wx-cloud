package cn.iocoder.wx.module.iot.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotMessageInboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 覆盖接入/重放与发送成功/失败的四种组合，确保异步落库故障可被运维发现。 */
class IotAsyncStatusLoggingTest {

    /** Kafka 已完成的异步结果不能掩盖数据库回写错误；日志须包含租户、事件和发送结果。 */
    @ParameterizedTest
    @CsvSource({"false,true", "false,false", "true,true", "true,false"})
    void shouldLogAsyncStatusFailure(boolean replay, boolean published) throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        IotEventPublisher publisher = mock(IotEventPublisher.class);
        CompletableFuture<SendResult<String, Object>> pending = new CompletableFuture<>();
        when(publisher.publish(any())).thenReturn(pending);
        RuntimeException databaseError = new IllegalStateException("database unavailable");
        Logger logger = (Logger) LoggerFactory.getLogger(replay
                ? IotMessageInboxServiceImpl.class : IotTelemetryService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        TenantContextHolder.setTenantId(7L);
        try {
            String eventId;
            if (replay) {
                IotMessageInboxMapper mapper = mock(IotMessageInboxMapper.class);
                IotMessageInboxServiceImpl service = new IotMessageInboxServiceImpl(mapper, json, publisher);
                IotMessageInboxDO inbox = new IotMessageInboxDO();
                inbox.setTenantId(7L);
                inbox.setConnectorId("connector-1");
                inbox.setStatus("FAILED");
                inbox.setPayloadJson(json.writeValueAsString(TwinTelemetryEvent.builder()
                        .eventId("event-1").connectorId("connector-1").tenantId(7L).build()));
                when(mapper.selectByEventId("event-1")).thenReturn(inbox);
                when(mapper.forceReclaim(anyString(), anyString(), any())).thenReturn(1);
                when(mapper.updateStatus(anyString(), anyString(), anyString(), any())).thenThrow(databaseError);
                when(mapper.markFailed(anyString(), anyString(), anyString(), anyInt())).thenThrow(databaseError);
                service.replay("event-1");
                eventId = "event-1";
            } else {
                IotMessageInboxService inbox = mock(IotMessageInboxService.class);
                when(inbox.tryClaim(any())).thenReturn("claim-1");
                doThrow(databaseError).when(inbox).markPublished(any(), anyString(), anyString());
                doThrow(databaseError).when(inbox).markFailed(any(), anyString(), anyString(), any());
                IotTelemetryService service = new IotTelemetryService(publisher,
                        mock(IotDeviceBindingService.class), new IotPointMappingService(json), inbox);
                eventId = service.ingest(request()).getEventId();
            }
            // 模拟 HTTP 已返回、Kafka 稍后完成；切换上下文验证日志不依赖线程当前租户。
            TenantContextHolder.setTenantId(8L);
            if (published) {
                pending.complete(null);
            } else {
                pending.completeExceptionally(new IllegalStateException("broker unavailable"));
            }
            ILoggingEvent log = appender.list.stream()
                    .filter(entry -> entry.getLevel() == Level.ERROR
                            && entry.getFormattedMessage().contains("异步投递状态更新失败"))
                    .findFirst().orElseThrow(() -> new AssertionError("异步落库异常未记录"));
            assertTrue(log.getFormattedMessage().contains("tenantId=7"));
            assertTrue(log.getFormattedMessage().contains("eventId=" + eventId));
            assertTrue(log.getFormattedMessage().contains("publishSucceeded=" + published));
            assertNotNull(log.getThrowableProxy());
            assertEquals(8L, TenantContextHolder.getTenantId());
        } finally {
            // 日志监听器必须解除并停止，避免后续测试收到旧日志或累积内存引用。
            logger.detachAppender(appender);
            appender.stop();
            TenantContextHolder.clear();
        }
    }

    /** 最小标准请求使用固定时间与追踪号，不在日志测试中依赖外部平台或真实凭据。 */
    private TwinTelemetryIngestRequest request() {
        TwinTelemetryIngestRequest request = new TwinTelemetryIngestRequest();
        request.setConnectorId("connector-1");
        request.setExternalDeviceId("external-1");
        request.setPointCode("temp");
        request.setValue(10);
        request.setValueType(IotValueType.NUMBER);
        request.setEventTime(Instant.parse("2026-10-17T12:00:00Z"));
        request.setTraceId("trace-1");
        return request;
    }
}
