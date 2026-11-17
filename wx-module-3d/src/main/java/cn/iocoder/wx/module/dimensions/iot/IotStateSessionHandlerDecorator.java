package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionHandlerDecorator;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/** 专用会话复用并发发送管理，不调用旧任务队列中断和历史重放。 */
public class IotStateSessionHandlerDecorator extends WebSocketSessionHandlerDecorator {
    private final IotStateSubscriptionRegistry registry;
    public IotStateSessionHandlerDecorator(WebSocketHandler delegate, WebSocketSessionManager manager,
                                           IotStateSubscriptionRegistry registry) {
        super(delegate, manager); this.registry = registry;
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try { registry.removeSession(session); }
        finally { super.afterConnectionClosed(session, status); }
    }
}
