package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 有界异步分发只取消逐条等待，保持旧构造语义和容量背压。 */
@SuppressWarnings("unchecked")
class KafkaWebSocketBackpressureTest {
    private final KafkaTemplate<Object, Object> kafka = mock(KafkaTemplate.class);
    private final WebSocketSessionManager sessions = mock(WebSocketSessionManager.class);

    @Test
    void asyncSendMustReturnWithoutWaitingForBrokerConfirmation() {
        var pending = new CompletableFuture<SendResult<Object, Object>>();
        when(kafka.send(anyString(), any())).thenReturn(pending);
        var sender = new KafkaWebSocketMessageSender(sessions, kafka, "scoped-ws", 2);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> sender.send("s1", "state", "{}"));
        verify(kafka).send(eq("scoped-ws"), argThat(value ->
                value instanceof cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessage message
                        && "s1".equals(message.getSessionId()) && "state".equals(message.getMessageType())));
        pending.complete(null);
    }

    @Test
    void fullCapacityMustWaitUntilEarlierConfirmationReleasesItsSlot() throws Exception {
        var first = new CompletableFuture<SendResult<Object, Object>>();
        var second = new CompletableFuture<SendResult<Object, Object>>();
        when(kafka.send(anyString(), any())).thenReturn(first, second);
        var sender = new KafkaWebSocketMessageSender(sessions, kafka, "scoped-ws", 1);
        sender.send("s1", "state", "{}");
        var caller = Executors.newSingleThreadExecutor();
        try {
            var waiting = caller.submit(() -> sender.send("s2", "state", "{}"));
            assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
            verify(kafka, times(1)).send(anyString(), any());
            first.complete(null);
            waiting.get(2, TimeUnit.SECONDS);
            verify(kafka, times(2)).send(anyString(), any());
            second.complete(null);
        } finally { caller.shutdownNow(); }
    }

    @Test
    void failureMustReleaseCapacityForTheNextSend() {
        var first = new CompletableFuture<SendResult<Object, Object>>();
        when(kafka.send(anyString(), any())).thenReturn(first, CompletableFuture.completedFuture(null));
        var sender = new KafkaWebSocketMessageSender(sessions, kafka, "scoped-ws", 1);
        sender.send("s1", "state", "{}");
        first.completeExceptionally(new IllegalStateException("代理不可达"));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> sender.send("s2", "state", "{}"));
        verify(kafka, times(2)).send(anyString(), any());
    }

    @Test
    void existingConstructorMustStillWaitForConfirmation() throws Exception {
        var pending = new CompletableFuture<SendResult<Object, Object>>();
        when(kafka.send(anyString(), any())).thenReturn(pending);
        var sender = new KafkaWebSocketMessageSender(sessions, kafka, "scoped-ws");
        var caller = Executors.newSingleThreadExecutor();
        try {
            var waiting = caller.submit(() -> sender.send("s1", "state", "{}"));
            assertThrows(TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
            pending.complete(null);
            waiting.get(2, TimeUnit.SECONDS);
        } finally { caller.shutdownNow(); }
    }
}
