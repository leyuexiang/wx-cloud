package cn.iocoder.wx.module.dimensions.iot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import cn.iocoder.wx.framework.websocket.core.listener.WebSocketMessageListener;

/** 处理三维运行时的物联网状态注销请求。 */
@Component
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateUnsubscribeWebSocketMessageListener implements WebSocketMessageListener<IotStateUnsubscriptionMessage> {
    private final IotStateSubscriptionRegistry registry;

    public IotStateUnsubscribeWebSocketMessageListener(IotStateSubscriptionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onMessage(WebSocketSession session, IotStateUnsubscriptionMessage message) {
        registry.unsubscribe(session, message);
    }

    @Override
    public String getType() {
        return "iot-state-unsubscribe";
    }
}
