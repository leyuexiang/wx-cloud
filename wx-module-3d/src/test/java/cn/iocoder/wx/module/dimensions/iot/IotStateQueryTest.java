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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证全局租户开关关闭、请求头伪造及机器人身份时仍拒绝跨租户访问。 */
class IotStateQueryTest {
    private final IotStateStore store = mock(IotStateStore.class);
    private final SecurityFrameworkService security = mock(SecurityFrameworkService.class);
    private final IotStateQueryService service = new IotStateQueryService(store, security);

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
}
