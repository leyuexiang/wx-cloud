package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.websocket.config.WebSocketProperties;
import cn.iocoder.wx.framework.websocket.core.handler.JsonWebSocketMessageHandler;
import cn.iocoder.wx.framework.websocket.core.session.WebSocketSessionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import java.util.List;

/** 专用端点和旧共享端点并存；配置错误在启动时失败，避免悄悄复用匿名入口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateWebSocketConfiguration implements WebSocketConfigurer {
    private final String path;
    private final WebSocketHandler handler;
    private final IotStateHandshakeInterceptor interceptor;
    public IotStateWebSocketConfiguration(WebSocketProperties properties, SecurityFrameworkService security,
            WebSocketSessionManager manager, IotStateSubscriptionRegistry registry,
            IotStateSubscribeWebSocketMessageListener subscribe, IotStateUnsubscribeWebSocketMessageListener unsubscribe,
            @Value("${wx.iot-state.websocket-path:}") String configuredPath) {
        path = resolvePath(properties.getPath(), configuredPath);
        handler = new IotStateSessionHandlerDecorator(new JsonWebSocketMessageHandler(List.of(subscribe, unsubscribe)), manager, registry);
        interceptor = new IotStateHandshakeInterceptor(security);
    }
    static String resolvePath(String shared, String configured) {
        String root = shared.replaceAll("/+$", "");
        String path = configured == null || configured.isBlank() ? root + "/iot-state" : configured.trim();
        if (!path.matches("/[A-Za-z0-9/_.-]+") || path.contains("//") || path.contains("..") || path.equals(root)
                || path.endsWith("/")) throw new IllegalArgumentException("物联网长连接路径无效或与共享端点冲突");
        return path;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 不注册为全局 HandshakeInterceptor Bean，防止专用准入被自动应用到旧端点。
        registry.addHandler(handler, path).addInterceptors(interceptor).setAllowedOriginPatterns("*");
    }
    @Bean public AuthorizeRequestsCustomizer iotStateWebSocketAuthorization() {
        return new AuthorizeRequestsCustomizer() {
            @Override public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // HTTP 过滤器继续解析访问令牌，最终准入在专用握手拦截器内判定。
                registry.requestMatchers(path).permitAll();
            }
        };
    }
}
