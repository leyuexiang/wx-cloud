package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 防止租户同名角色或请求租户变更获得平台权限。 */
class ArtResourceAccessTest {
    private ArtResourceAccess access; private PermissionApi permissions;
    @BeforeEach void setup(){access=new ArtResourceAccess();permissions=mock(PermissionApi.class);ReflectionTestUtils.setField(access,"permissions",permissions);ReflectionTestUtils.setField(access,"platformTenant",1L);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    @Test void anonymousDenied(){assertThrows(ServiceException.class,()->access.actor());}
    @Test void forgedTenantDenied(){login(2);TenantContextHolder.setTenantId(1L);assertThrows(ServiceException.class,()->access.actor());}
    @Test void authenticatedIdentityUsed(){login(2);assertEquals(2,access.actor().tenant());}
    @Test void platformRoleAndTenantRequired(){when(permissions.hasAnyRoles(10L,"super_admin")).thenReturn(CommonResult.success(true));assertDoesNotThrow(()->access.maintain(new ArtResourceAccess.Actor(10,1),"GLOBAL_SYSTEM","0"));assertThrows(ServiceException.class,()->access.maintain(new ArtResourceAccess.Actor(10,2),"GLOBAL_SYSTEM","0"));}
    @Test void missingPlatformConfigurationFailsClosed(){ReflectionTestUtils.setField(access,"platformTenant",0L);assertThrows(ServiceException.class,()->access.maintain(new ArtResourceAccess.Actor(10,1),"GLOBAL_SYSTEM","0"));}
    @Test void tenantManagerOnlyOwnTenant(){when(permissions.hasAnyRoles(10L,"tenant_admin")).thenReturn(CommonResult.success(true));assertDoesNotThrow(()->access.maintain(new ArtResourceAccess.Actor(10,2),"TENANT_SHARED","2"));assertThrows(ServiceException.class,()->access.maintain(new ArtResourceAccess.Actor(10,2),"TENANT_SHARED","3"));}
    @Test void viewerCannotMaintain(){when(permissions.hasAnyRoles(10L,"tenant_admin")).thenReturn(CommonResult.success(false));assertThrows(ServiceException.class,()->access.maintain(new ArtResourceAccess.Actor(10,2),"TENANT_SHARED","2"));}
    @Test void platformDoesNotAutomaticallyMaintainShared(){when(permissions.hasAnyRoles(10L,"tenant_admin")).thenReturn(CommonResult.success(false));assertThrows(ServiceException.class,()->access.maintain(new ArtResourceAccess.Actor(10,1),"TENANT_SHARED","1"));}
    private void login(long tenant){LoginUser user=new LoginUser();user.setId(10L);user.setTenantId(tenant);TenantContextHolder.setTenantId(tenant);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));}
}
