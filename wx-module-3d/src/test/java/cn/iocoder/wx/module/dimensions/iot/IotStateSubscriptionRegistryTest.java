package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.websocket.core.util.WebSocketFrameworkUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证消息线程没有 HTTP 身份时，订阅仍以握手身份检查权限。 */
class IotStateSubscriptionRegistryTest {
    @Test
    void shouldMatchOnlySavedObjectAndItsConfiguredPoint() throws Exception {
        var scene = new ObjectMapper().readTree("""
                {"objects":[{"id":"pump","type":"model","metadata":{"dataConfig":{"requests":[
                {"request":{"type":"adapter","config":{"adapterId":"iot-state",
                "config":{"deviceId":"d1","pointCode":"temperature"}}}}
                ]}}}]}
                """);
        assertTrue(IotSceneBindingScope.hasBinding(scene, "pump", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(scene, "other", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(scene, "pump", "d1", "pressure"));
        var event = TwinTelemetryEvent.builder().sceneId("10").objectId("pump").build();
        assertTrue(IotSceneBindingScope.matchesEvent(event, "10", "pump"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "11", "pump"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "10", "other"));
    }
    @Test
    void shouldCheckHandshakeIdentityAndRestoreContext() {
        var session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        when(session.getAttributes()).thenReturn(attributes);
        var security = mock(SecurityFrameworkService.class);
        var redis = mock(StringRedisTemplate.class);
        var registry = new IotStateSubscriptionRegistry(null, security, redis, mock(IotSceneBindingScope.class));
        var request = new IotStateSubscriptionMessage();
        request.setRequestId("request");
        var user = new LoginUser().setId(42L).setTenantId(7L).setUserType(UserTypeEnum.ADMIN.getValue())
                .setExpiresTime(LocalDateTime.now().plusMinutes(5));
        var original = SecurityContextHolder.getContext();
        WebSocketFrameworkUtils.setLoginUser(user, attributes);
        when(security.hasPermission(IotStateSubscriptionRegistry.SUBSCRIBE_PERMISSION)).thenAnswer(call -> {
            assertSame(user, SecurityFrameworkUtils.getLoginUser());
            assertEquals(7L, TenantContextHolder.getTenantId());
            return false;
        });
        assertThrows(AccessDeniedException.class, () -> registry.subscribe(session, request));
        assertSame(original, SecurityContextHolder.getContext());
        verify(security).hasPermission(IotStateSubscriptionRegistry.SUBSCRIBE_PERMISSION);
        user.setExpiresTime(LocalDateTime.now().minusMinutes(1));
        assertThrows(AccessDeniedException.class, () -> registry.subscribe(session, request));
        verifyNoMoreInteractions(security);
        verifyNoInteractions(redis);
    }
}
