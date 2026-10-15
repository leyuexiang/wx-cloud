package cn.iocoder.jtyf.module.dimensions.config;

import cn.iocoder.jtyf.framework.websocket.core.handler.JsonWebSocketMessageHandler;
import cn.iocoder.jtyf.framework.websocket.core.listener.WebSocketMessageListener;
import cn.iocoder.jtyf.framework.websocket.core.session.WebSocketSessionManager;
import cn.iocoder.jtyf.module.dimensions.websocket.session.MessageSessionHandlerDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.WebSocketHandler;

import java.util.List;

/**
 * @author: cheYu
 * @desc: 旨在覆盖父依赖websocket内的WebSocketHandler相关配置
 * @create: 2025-06-18 11:43
 */
@Configuration(proxyBeanMethods = false)
public class MessageWebSocketConfiguration {

    // 需要定义新WebSocketHandler的名称，不然会和上级WebSocketHandler冲突
    @Bean("messageSessionHandlerDecorator")
    @Primary
    public WebSocketHandler webSocketHandler(WebSocketSessionManager sessionManager,
                                             List<? extends WebSocketMessageListener<?>> messageListeners) {
        // 1. 创建 JsonWebSocketMessageHandler 对象，处理消息
        JsonWebSocketMessageHandler messageHandler = new JsonWebSocketMessageHandler(messageListeners);
        // 2. 创建 MessageSessionHandlerDecorator 对象，处理连接
        return new MessageSessionHandlerDecorator(messageHandler, sessionManager);
    }
}
