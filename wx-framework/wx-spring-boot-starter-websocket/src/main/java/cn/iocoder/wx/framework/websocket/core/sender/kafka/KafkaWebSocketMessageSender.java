package cn.iocoder.wx.framework.websocket.core.sender.kafka;

import cn.iocoder.wx.framework.websocket.core.sender.AbstractWebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Kafka 的 {@link WebSocketMessageSender} 实现类
 *
 * @author 九天芸菲
 */
@Slf4j
public class KafkaWebSocketMessageSender extends AbstractWebSocketMessageSender {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    private final String topic;
    private final Semaphore pending;

    public KafkaWebSocketMessageSender(WebSocketSessionManager sessionManager,
                                       KafkaTemplate<Object, Object> kafkaTemplate,
                                       String topic) {
        this(sessionManager, kafkaTemplate, topic, 0);
    }

    /** 零上限保留旧同步语义；启用时只保留有界的待确认消息，不创建额外线程。 */
    public KafkaWebSocketMessageSender(WebSocketSessionManager sessionManager,
                                       KafkaTemplate<Object, Object> kafkaTemplate, String topic, int maxPending) {
        super(sessionManager);
        if (maxPending < 0 || maxPending > 4096) throw new IllegalArgumentException("WebSocket 待确认上限无效");
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.pending = maxPending == 0 ? null : new Semaphore(maxPending);
    }

    @Override
    public void send(Integer userType, Long userId, String messageType, String messageContent) {
        sendKafkaMessage(null, userId, userType, messageType, messageContent);
    }

    @Override
    public void send(Integer userType, String messageType, String messageContent) {
        sendKafkaMessage(null, null, userType, messageType, messageContent);
    }

    @Override
    public void send(String sessionId, String messageType, String messageContent) {
        sendKafkaMessage(sessionId, null, null, messageType, messageContent);
    }

    /**
     * 通过 Kafka 广播消息
     *
     * @param sessionId Session 编号
     * @param userId 用户编号
     * @param userType 用户类型
     * @param messageType 消息类型
     * @param messageContent 消息内容
     */
    private void sendKafkaMessage(String sessionId, Long userId, Integer userType,
                                  String messageType, String messageContent) {
        KafkaWebSocketMessage mqMessage = new KafkaWebSocketMessage()
                .setSessionId(sessionId).setUserId(userId).setUserType(userType)
                .setMessageType(messageType).setMessageContent(messageContent);
        if (pending != null) {
            sendBoundedAsync(mqMessage);
            return;
        }
        try {
            kafkaTemplate.send(topic, mqMessage).get();
        } catch (InterruptedException | ExecutionException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.error("WebSocket Kafka 发送失败，failureType={}", e.getClass().getSimpleName());
        }
    }

    private void sendBoundedAsync(KafkaWebSocketMessage message) {
        boolean acquired = false;
        try {
            // Kafka 慢或不可用时施加背压，最长等待五秒；当前态仍可通过授权快照补偿。
            acquired = pending.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("WebSocket Kafka 待确认配额等待超时");
                return;
            }
            kafkaTemplate.send(topic, message).whenComplete((result, failure) -> {
                pending.release();
                if (failure != null) log.error("WebSocket Kafka 异步发送失败，failureType={}", failure.getClass().getSimpleName());
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (acquired) pending.release();
            log.warn("WebSocket Kafka 待确认等待已中断");
        } catch (RuntimeException error) {
            if (acquired) pending.release();
            log.error("WebSocket Kafka 提交失败，failureType={}", error.getClass().getSimpleName());
        }
    }

}
