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
        assertTrue(IotSceneBindingScope.hasBinding(new IotSceneBindingScope.SavedScene(scene, null), "pump", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(new IotSceneBindingScope.SavedScene(scene, null), "other", "d1", "temperature"));
        assertFalse(IotSceneBindingScope.hasBinding(new IotSceneBindingScope.SavedScene(scene, null), "pump", "d1", "pressure"));
        var event = TwinTelemetryEvent.builder().sceneId("10").objectId("pump").build();
        assertTrue(IotSceneBindingScope.matchesEvent(event, "10", "pump"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "11", "pump"));
        assertFalse(IotSceneBindingScope.matchesEvent(event, "10", "other"));
    }
    @Test
    void shouldMatchEditorDependencyByExistingThreeDimensionalOwner() throws Exception {
        // 网页保存把模型和几何体拆成两个集合，适配器请求写入独立依赖包。
        var json = new ObjectMapper();
        var scene = json.readTree("""
                {"objects":[{"instanceId":"model","targetType":"model"}],
                 "components":[{"instanceId":"cube","targetType":"primitive"}]}
                """);
        var dependencies = json.readTree("""
                {"dataBindings":[
                  {"owner":{"domain":"threeD","ownerType":"instance","instanceId":"model"},
                   "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state",
                     "config":{"deviceId":"d1","pointCode":"visible"}}},"enabled":true},
                  {"owner":{"domain":"twoD","ownerType":"instance","instanceId":"model"},
                   "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state",
                     "config":{"deviceId":"d2","pointCode":"visible"}}},"enabled":true},
                  {"owner":{"domain":"threeD","ownerType":"instance","instanceId":"cube"},
                   "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state",
                     "config":{"deviceId":"d1","pointCode":"visible"}}},"enabled":false}],
                 "deviceBindings":[
                  {"owner":{"domain":"threeD","ownerType":"instance","instanceId":"cube"},
                   "deviceId":"d3","pointCode":"motion"},
                  {"owner":{"domain":"threeD","ownerType":"instance","instanceId":"removed"},
                   "deviceId":"d1","pointCode":"visible"}]}
                """);
        var saved = new IotSceneBindingScope.SavedScene(scene, dependencies);
        assertTrue(IotSceneBindingScope.hasBinding(saved, "model", "d1", "visible"));
        assertTrue(IotSceneBindingScope.hasBinding(saved, "cube", "d3", "motion"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "model", "d2", "visible"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "cube", "d1", "visible"));
        assertFalse(IotSceneBindingScope.hasBinding(saved, "removed", "d1", "visible"));
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
