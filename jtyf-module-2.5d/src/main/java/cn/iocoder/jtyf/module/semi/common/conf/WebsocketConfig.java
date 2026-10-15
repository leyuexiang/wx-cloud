package cn.iocoder.jtyf.module.semi.common.conf;

import cn.hutool.core.collection.CollUtil;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.List;
import java.util.Map;

/**
 * @author wp
 */
@Configuration
public class WebsocketConfig extends ServerEndpointConfig.Configurator {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        final Map<String, Object> userProperties = sec.getUserProperties();
        Map<String, List<String>> headers = request.getHeaders();
        Map<String, List<String>> params = request.getParameterMap();
        List<String> userId = params.get("userId");
        List<String> fromHeader = params.get("from");
        if (CollUtil.isEmpty(userId) || CollUtil.isEmpty(fromHeader)) {
            throw new RuntimeException("请求头有误，参数不完整");
        }
        userProperties.put("userId", userId.get(0));
        userProperties.put("from", fromHeader.get(0));

        if (CollUtil.isNotEmpty(headers.get("Sec-WebSocket-Protocol"))) {
            response.getHeaders().put("Sec-WebSocket-Protocol", headers.get("Sec-WebSocket-Protocol"));
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, CollUtil.newArrayList("*"));
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, CollUtil.newArrayList("*"));
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, CollUtil.newArrayList("*"));
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, CollUtil.newArrayList("true"));
            response.getHeaders().put(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, CollUtil.newArrayList("*"));
        }
    }
}
