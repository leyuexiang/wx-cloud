package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.common.enums.UserTypeEnum;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证全局租户开关关闭、请求头伪造及机器人身份时仍拒绝跨租户访问。 */
class IotStateQueryTest {
    private final IotStateStore store = mock(IotStateStore.class);
    private final SecurityFrameworkService security = mock(SecurityFrameworkService.class);
    private final IotStateQueryService service = new IotStateQueryService(store, security, mock(IotSceneBindingScope.class));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private void authenticate(Long tenantId, Integer userType) {
        LoginUser user = new LoginUser();
        user.setId(42L);
        user.setTenantId(tenantId);
        user.setUserType(userType);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList()));
    }

    @Test
    void shouldUseAuthenticatedTenantForPermissionAndReadThenRestoreContext() {
        authenticate(7L, UserTypeEnum.ADMIN.getValue());
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        when(security.hasPermission(IotStateQueryService.QUERY_PERMISSION)).thenAnswer(invocation -> {
            assertEquals(7L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return true;
        });
        service.get("device-1", "temperature");
        verify(store).get(7L, "device-1", "temperature");
        assertEquals(999L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void shouldDenyMissingPermissionWithoutReadingRedis() {
        authenticate(7L, UserTypeEnum.ADMIN.getValue());
        assertThrows(AccessDeniedException.class, () -> service.get("device-1", "temperature"));
        verifyNoInteractions(store);
    }

    @Test
    void shouldDenyUnauthenticatedRequestsDespiteLegacyPermitAll() {
        assertThrows(AccessDeniedException.class, () -> service.get("device-1", "temperature"));
        verifyNoInteractions(store, security);
    }

    @Test
    void shouldDenyBotAndMissingTenantEvenWhenFrameworkPermissionWouldAllow() {
        authenticate(7L, UserTypeEnum.BOT.getValue());
        assertThrows(AccessDeniedException.class, () -> service.get("device-1", "temperature"));
        authenticate(null, UserTypeEnum.ADMIN.getValue());
        assertThrows(AccessDeniedException.class, () -> service.get("device-1", "temperature"));
        verifyNoInteractions(store, security);
    }

    @Test
    void shouldUseSameWidgetScopeForDraftAndFixedPublicationWithoutTrustingSourceObjectId() throws Exception {
        authenticate(7L, UserTypeEnum.ADMIN.getValue());
        when(security.hasPermission(IotStateQueryService.QUERY_PERMISSION)).thenReturn(true);
        var scope = mock(IotSceneBindingScope.class);
        var query = new IotStateQueryService(store, security, scope);
        var json = new ObjectMapper();
        var saved = new IotSceneBindingScope.SavedScene(json.readTree("{}"), json.readTree("""
                {"dataBindings":[{"owner":{"domain":"twoD","ownerType":"instance","instanceId":"widget"},
                  "source":{"type":"adapter","requestConfig":{"adapterId":"iot-state","config":{"deviceId":"d1","pointCode":"temperature"}}}}]}
                """), json.readTree("{\"components\":[{\"instanceId\":\"widget\"}]}"));
        when(scope.read("10")).thenReturn(saved);
        when(scope.readPublished("PUB-1", "ticket", 7L)).thenReturn(new IotSceneBindingScope.ScopedScene("10", "t1", saved));
        var event = cn.iocoder.wx.module.iot.api.TwinTelemetryEvent.builder().sceneId("10").objectId("source-model").build();
        when(store.get(7L, "d1", "temperature")).thenReturn(event);
        assertSame(event, query.get("d1", "temperature", "10", "widget", "twoD"));
        assertSame(event, query.getPublished("PUB-1", "ticket", "widget", "d1", "temperature", "twoD"));
        assertThrows(AccessDeniedException.class, () -> query.get("d1", "temperature", "10", "widget", null));
        event.setSceneId("11");
        assertNull(query.get("d1", "temperature", "10", "widget", "twoD"));
    }
}
