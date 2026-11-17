package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 专用物联网端点在升级前检查真实身份，失败不建立会话或登记订阅。 */
public class IotStateHandshakeInterceptor implements HandshakeInterceptor {
    public static final Set<String> OUTBOUND_TYPES = Set.of("iot-state-update", "iot-state-subscribe-ack",
            "iot-state-subscribe-error", "iot-state-unsubscribe-ack");
    private final SecurityFrameworkService security;
    public IotStateHandshakeInterceptor(SecurityFrameworkService security) { this.security = security; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        response.getHeaders().setCacheControl("no-store");
        var user = SecurityFrameworkUtils.getLoginUser();
        if (user == null || user.getId() == null || user.getId() <= 0 || user.getExpiresTime() == null
                || !user.getExpiresTime().isAfter(LocalDateTime.now())) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED); return false;
        }
        if (!UserTypeEnum.ADMIN.getValue().equals(user.getUserType()) || user.getTenantId() == null || user.getTenantId() < 0) {
            response.setStatusCode(HttpStatus.FORBIDDEN); return false;
        }
        // 显式租户只用于一致性检查，不能替换令牌中已认证的租户。
        String tenant = user.getTenantId().toString();
        var headers = request.getHeaders().get("tenant-id");
        var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().get("tenant-id");
        if ((headers != null && (headers.size() != 1 || !tenant.equals(headers.get(0))))
                || (query != null && (query.size() != 1 || !tenant.equals(query.get(0))))) {
            response.setStatusCode(HttpStatus.FORBIDDEN); return false;
        }
        try {
            var permitted = new AtomicBoolean();
            TenantUtils.execute(user.getTenantId(), (Runnable) () -> permitted.set(
                    security.hasPermission(IotStateSubscriptionRegistry.SUBSCRIBE_PERMISSION)));
            if (!permitted.get()) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
        } catch (Exception unavailable) {
            // 权限服务故障时关闭准入，不记录请求地址、令牌或身份正文。
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE); return false;
        }
        WebSocketFrameworkUtils.setLoginUser(user, attributes);
        WebSocketFrameworkUtils.setAllowedMessageTypes(OUTBOUND_TYPES, attributes);
        return true;
    }

    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                          WebSocketHandler handler, Exception exception) { }
}
