package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.web.config.WebProperties;
import cn.iocoder.wx.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.wx.module.dimensions.config.Dim3dPlatformConfiguration;
import cn.iocoder.wx.module.dimensions.controller.admin.GisCatalogController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 使用实际端别拦截器验证认证边界，不信任请求头声明的运营或租户身份。 */
class GisCatalogControllerTest {
    private GisCatalogService catalog;
    private MockMvc mvc;
    @BeforeEach void setup() {
        var controller = new GisCatalogController();
        var access = new ArtResourceAccess(); ReflectionTestUtils.setField(access, "platformTenant", 1L);
        catalog = mock(GisCatalogService.class);
        when(catalog.list(anyString())).thenReturn(List.of());
        ReflectionTestUtils.setField(controller, "catalog", catalog);
        ReflectionTestUtils.setField(controller, "access", access);
        var web = new WebProperties();
        var interceptor = new Dim3dPlatformConfiguration().dim3dPlatformAccess(web, access);
        mvc = MockMvcBuilders.standaloneSetup(controller).setCustomHandlerMapping(() -> {
            var mapping = new RequestMappingHandlerMapping();
            mapping.setPathPrefixes(Map.of(web.getAdminApi().getPrefix(), type -> true)); return mapping;
        }).addMappedInterceptors(interceptor.getPathPatterns(), interceptor.getInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler("gis-test", null)).build();
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); TenantContextHolder.clear(); }
    private void login(long tenant) {
        var user = new LoginUser(); user.setId(10L); user.setTenantId(tenant);
        TenantContextHolder.setTenantId(tenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
    @Test void anonymousReadsAreRejectedBeforeCatalogAccess() throws Exception {
        mvc.perform(get("/admin-api/dim3d/tenant/webgis/info/list").param("dataType", "SATELLITE"))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(catalog);
    }
    @Test void tenantReadsOnlyItsPlatformAndHeadersCannotPromoteIdentity() throws Exception {
        login(2L);
        mvc.perform(get("/admin-api/dim3d/tenant/webgis/info/list").param("dataType", "SATELLITE"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin-api/dim3d/operation/webgis/info/list").param("dataType", "ELEVATION").header("tenant-id", "1"))
                .andExpect(jsonPath("$.code").value(403));
        verify(catalog, times(1)).list(anyString());
    }
    @Test void operationReadsAndLegacyReadKeepAuthentication() throws Exception {
        login(1L);
        mvc.perform(get("/admin-api/dim3d/operation/webgis/info/list").param("dataType", "ELEVATION"))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/admin-api/dim3d/tenant/webgis/info/list").param("dataType", "ELEVATION"))
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(get("/admin-api/dim3d/webgis/info/list").param("dataType", "SATELLITE"))
                .andExpect(jsonPath("$.code").value(200));
    }
}
