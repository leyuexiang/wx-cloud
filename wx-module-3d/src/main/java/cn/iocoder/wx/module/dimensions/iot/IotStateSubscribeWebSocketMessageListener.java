package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.listener.WebSocketMessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** 处理三维运行时的物联网状态订阅请求。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateSubscribeWebSocketMessageListener implements WebSocketMessageListener<IotStateSubscriptionMessage> {
    private final IotStateSubscriptionRegistry registry;

    public IotStateSubscribeWebSocketMessageListener(IotStateSubscriptionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onMessage(WebSocketSession session, IotStateSubscriptionMessage message) {
        try {
            registry.subscribe(session, message);
        } catch (AccessDeniedException exception) {
            registry.sendError(session, message == null ? null : message.getRequestId(), "FORBIDDEN", exception.getMessage());
        } catch (RuntimeException exception) {
            // 依赖故障应与权限拒绝区分，内部异常仅写服务日志。
            log.warn("物联网状态订阅失败，session={}", session.getId(), exception);
            registry.sendError(session, message == null ? null : message.getRequestId(), "UNAVAILABLE", "物联网订阅暂不可用");
        }
    }

    @Override
    public String getType() {
        return "iot-state-subscribe";
    }
}
