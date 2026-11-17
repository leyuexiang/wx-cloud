package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.connection.RedisConnection;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 批量往返仍逐项比较会话登记，不能让已撤销或替换的订阅借用其他人的续租。 */
@SuppressWarnings({"unchecked", "rawtypes"})
class IotSubscriptionDeliveryBatchTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> indexes = mock(ZSetOperations.class);
    private final WebSocketMessageSender sender = mock(WebSocketMessageSender.class);

    private String binding(int index) {
        return "{\"subscriptionId\":\"sub-" + index + "\",\"sessionId\":\"session-" + index
                + "\",\"tenantId\":7,\"sceneId\":\"10\",\"objectId\":\"widget\",\"deviceId\":\"d1\",\"pointCode\":\"temperature\",\"domain\":\"twoD\"}";
    }

    private IotStateSubscriptionRegistry registry(List<String> rows) {
        when(redis.opsForZSet()).thenReturn(indexes);
        when(indexes.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(new LinkedHashSet<>(rows));
        return new IotStateSubscriptionRegistry(sender, null, redis, mock(IotSceneBindingScope.class));
    }

    private TwinTelemetryEvent event() {
        return TwinTelemetryEvent.builder().tenantId(7L).sceneId("10").objectId("source-model")
                .deviceId("d1").pointCode("temperature").eventId("event").build();
    }

    @Test
    void mustUseBoundedPipelineBatchesInsteadOfOneRoundTripPerViewer() {
        var rows = new ArrayList<String>();
        for (int i = 0; i < 300; i++) rows.add(binding(i));
        var registry = registry(rows);
        var offset = new AtomicInteger();
        var requests = new AtomicInteger();
        when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(call -> {
            requests.incrementAndGet();
            RedisConnection connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
            ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection);
            var invocations = mockingDetails(connection.hashCommands()).getInvocations().size();
            assertTrue(invocations > 0 && invocations <= 128);
            int start = offset.getAndAdd(invocations);
            return new ArrayList<>(rows.subList(start, start + invocations));
        });
        registry.publish(event(), "v1");
        assertEquals(3, requests.get());
        assertEquals(300, offset.get());
        verify(sender, times(300)).sendObject(anyString(), eq("iot-state-update"), any());
        verify(redis, never()).opsForHash();
    }

    @Test
    void replacedOrMissingRegistrationMustNotReceiveAnUpdate() {
        var rows = List.of(binding(0), binding(1), binding(2));
        var registry = registry(rows);
        when(redis.executePipelined(any(RedisCallback.class))).thenReturn(Arrays.asList(rows.get(0), null, "new-registration"));
        registry.publish(event(), "v1");
        verify(sender).sendObject(eq("session-0"), eq("iot-state-update"), any());
        verifyNoMoreInteractions(sender);
    }
}
