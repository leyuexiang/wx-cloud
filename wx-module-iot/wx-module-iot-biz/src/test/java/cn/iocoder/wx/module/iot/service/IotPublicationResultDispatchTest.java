package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.api.IotValueType;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 生产者完成通知只调度回写；饱和时回退当前线程，不能丢失发布结果。 */
class IotPublicationResultDispatchTest {
    private final IotEventPublisher publisher = mock(IotEventPublisher.class);
    private final IotDeviceBindingService bindings = mock(IotDeviceBindingService.class);
    private final IotPointMappingService mapping = mock(IotPointMappingService.class);
    private final IotMessageInboxService inbox = mock(IotMessageInboxService.class);
    private final IotTelemetryService service = new IotTelemetryService(publisher, bindings, mapping, inbox);
    private final CompletableFuture<SendResult<String, Object>> completion = new CompletableFuture<>();

    private String ingest() {
        when(mapping.map(isNull(), anyString(), any(), any(), isNull()))
                .thenReturn(new IotMappedPoint("temperature", 1, IotValueType.NUMBER, null));
        when(inbox.tryClaim(any())).thenReturn("claim-v1");
        when(publisher.publish(any())).thenReturn(completion);
        var request = new TwinTelemetryIngestRequest();
        request.setConnectorId("pilot"); request.setExternalDeviceId("d1"); request.setPointCode("raw");
        request.setValue(1); request.setValueType(IotValueType.NUMBER); request.setTenantId(7L);
        request.setEventTime(Instant.now()); request.setTraceId("dispatch-test");
        return service.ingest(request).getEventId();
    }

    @Test
    void successfulKafkaCompletionMustNotRunDatabaseWriteOnProducerThread() {
        List<Runnable> pending = new ArrayList<>();
        service.setPublicationResultExecutor(pending::add);
        String event = ingest();
        assertTrue(completion.complete(null));
        verify(inbox, never()).markPublished(anyLong(), anyString(), anyString());
        assertEquals(1, pending.size());
        pending.get(0).run();
        verify(inbox).markPublished(7L, event, "claim-v1");
    }

    @Test
    void failedKafkaCompletionMustRecordTheOriginalFailureAfterDispatch() {
        List<Runnable> pending = new ArrayList<>();
        service.setPublicationResultExecutor(pending::add);
        String event = ingest();
        var failure = new IllegalStateException("发送失败");
        completion.completeExceptionally(failure);
        verify(inbox, never()).markFailed(anyLong(), anyString(), anyString(), any());
        pending.get(0).run();
        verify(inbox).markFailed(7L, event, "claim-v1", failure);
    }

    @Test
    void saturatedOrClosedExecutorMustFallBackWithoutDroppingTheResult() {
        service.setPublicationResultExecutor(task -> { throw new RejectedExecutionException(); });
        String event = ingest();
        completion.complete(null);
        verify(inbox, times(1)).markPublished(7L, event, "claim-v1");
    }
}
