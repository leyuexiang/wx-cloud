package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.websocket.core.sender.local.LocalWebSocketMessageSender;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 复用发送器及会话管理，同时隔离旧任务广播和新连接时的历史重放。 */
class IotStateEndpointIsolationTest {
    @Test void derivesPathAndRejectsCollisionOrQuery() {
        assertEquals("/dimensions/ws/iot-state", IotStateWebSocketConfiguration.resolvePath("/dimensions/ws", ""));
        assertEquals("/custom/stream", IotStateWebSocketConfiguration.resolvePath("/dimensions/ws", "/custom/stream"));
        assertThrows(IllegalArgumentException.class, () -> IotStateWebSocketConfiguration.resolvePath("/dimensions/ws", "/dimensions/ws"));
        assertThrows(IllegalArgumentException.class, () -> IotStateWebSocketConfiguration.resolvePath("/dimensions/ws", "/a?token=x"));
    }
    @Test void onlyAllowsDeclaredTypesOnDedicatedSessionsAndKeepsLegacySessions() throws Exception {
        var manager = mock(WebSocketSessionManager.class);
        var sender = new LocalWebSocketMessageSender(manager);
        var ordinary = mock(WebSocketSession.class); var dedicated = mock(WebSocketSession.class);
        when(ordinary.getAttributes()).thenReturn(new HashMap<>()); when(ordinary.isOpen()).thenReturn(true);
        var attributes = new HashMap<String, Object>();
        WebSocketFrameworkUtils.setAllowedMessageTypes(IotStateHandshakeInterceptor.OUTBOUND_TYPES, attributes);
        when(dedicated.getAttributes()).thenReturn(attributes); when(dedicated.isOpen()).thenReturn(true);
        sender.doSend(List.of(ordinary, dedicated), "legacy-notice", "{}");
        verify(ordinary).sendMessage(any()); verify(dedicated, never()).sendMessage(any());
        sender.doSend(List.of(dedicated), "iot-state-update", "{}"); verify(dedicated).sendMessage(any());
        sender.doSend(List.of(ordinary, dedicated), "old-raw-body");
        verify(ordinary, times(2)).sendMessage(any()); verify(dedicated, times(1)).sendMessage(any());
    }
    @Test void addsSessionWithoutReplayingAndCleansSubscriptionOnClose() {
        var manager = mock(WebSocketSessionManager.class); var registry = mock(IotStateSubscriptionRegistry.class);
        var handler = new IotStateSessionHandlerDecorator(mock(WebSocketHandler.class), manager, registry);
        var session = mock(WebSocketSession.class);
        handler.afterConnectionEstablished(session); verify(manager).addSession(any()); verifyNoInteractions(registry);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(registry).removeSession(session); verify(manager).removeSession(session);
    }
}
