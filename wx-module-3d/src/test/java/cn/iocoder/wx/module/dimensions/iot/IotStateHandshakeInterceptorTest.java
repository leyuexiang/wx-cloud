package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 专用入口在升级之前拒绝无身份、过期和无权限请求，不影响旧握手拦截器。 */
class IotStateHandshakeInterceptorTest {
    private final SecurityFrameworkService security = mock(SecurityFrameworkService.class);
    private final IotStateHandshakeInterceptor interceptor = new IotStateHandshakeInterceptor(security);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dimensions/ws/iot-state");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final HashMap<String, Object> attributes = new HashMap<>();
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private LoginUser login() {
        var user = new LoginUser().setId(10L).setTenantId(2L).setUserType(2).setExpiresTime(LocalDateTime.now().plusMinutes(5));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }
    private boolean handshake() {
        return interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), null, attributes);
    }
    @Test void rejectsAnonymousBeforeUpgrade() {
        assertFalse(handshake()); assertEquals(401, response.getStatus()); assertTrue(attributes.isEmpty()); verifyNoInteractions(security);
    }
    @Test void rejectsExpiredIdentityWithoutCheckingPermissions() {
        login().setExpiresTime(LocalDateTime.now().minusSeconds(1));
        assertFalse(handshake()); assertEquals(401, response.getStatus()); verifyNoInteractions(security);
    }
    @Test void rejectsDifferentTenantBeforeCheckingPermissions() {
        login(); request.addHeader("tenant-id", "3");
        assertFalse(handshake()); assertEquals(403, response.getStatus()); verifyNoInteractions(security);
    }
    @Test void rejectsMissingTenantAndNonAdminIdentity() {
        var user = login().setTenantId(null);
        assertFalse(handshake()); assertEquals(403, response.getStatus());
        user.setTenantId(2L).setUserType(1);
        assertFalse(handshake()); verifyNoInteractions(security);
    }
    @Test void rejectsConflictingOrRepeatedQueryTenant() {
        login();
        request.setQueryString("tenant-id=3");
        assertFalse(handshake()); assertEquals(403, response.getStatus());
        request.setQueryString("tenant-id=2&tenant-id=2");
        assertFalse(handshake()); verifyNoInteractions(security);
    }
    @Test void rejectsMissingPermissionAndPermissionServiceFailure() {
        login(); assertFalse(handshake()); assertEquals(403, response.getStatus());
        when(security.hasPermission(IotStateSubscriptionRegistry.SUBSCRIBE_PERMISSION)).thenThrow(new IllegalStateException());
        assertFalse(handshake()); assertEquals(503, response.getStatus()); assertTrue(attributes.isEmpty());
    }
    @Test void keepsTrustedIdentityAndRestrictsOutboundMessageTypes() {
        var user = login(); request.addHeader("tenant-id", "2");
        when(security.hasPermission(IotStateSubscriptionRegistry.SUBSCRIBE_PERMISSION)).thenReturn(true);
        assertTrue(handshake()); assertSame(user, attributes.get(WebSocketFrameworkUtils.ATTRIBUTE_LOGIN_USER));
        assertSame(user, SecurityFrameworkUtils.getLoginUser());
        assertEquals(IotStateHandshakeInterceptor.OUTBOUND_TYPES, attributes.get(WebSocketFrameworkUtils.ATTRIBUTE_ALLOWED_MESSAGE_TYPES));
    }
}
