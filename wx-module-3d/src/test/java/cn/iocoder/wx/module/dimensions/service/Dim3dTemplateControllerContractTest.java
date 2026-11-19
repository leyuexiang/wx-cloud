package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.web.config.WebProperties;
import cn.iocoder.wx.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.wx.module.dimensions.config.Dim3dPlatformConfiguration;
import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dResourceController;
import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dSceneController;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 按公开路由验证文件型模板分页，防止响应额外嵌套并回显租户字段。 */
class Dim3dTemplateControllerContractTest {
    private static final String BODY = """
            {"templateName":"示例模板","templateType":"INDUSTRY","category":"PARK_INDUSTRY",
             "resourceBelong":"MINE","templateFileId":"9007199254740994"}
            """;
    private Dim3dResourceCatalogService catalog;
    private Dim3dSceneService scene;
    private Dim3dResourceController controller;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        catalog = mock(Dim3dResourceCatalogService.class);
        scene = mock(Dim3dSceneService.class);
        controller = new Dim3dResourceController();
        ReflectionTestUtils.setField(controller, "service", catalog);
        ReflectionTestUtils.setField(controller, "sceneService", scene);
        ReflectionTestUtils.setField(controller, "catalogResponses", new Dim3dCatalogResponseAssembler());
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** 两端共用模板表单及状态权限，端别错误时不能调用任何模板业务。 */
    @Test
    void platformPathsAcceptOwnSessionAndRejectCrossPlatformAccess() throws Exception {
        var snapshots = new Dim3dSceneController();
        ReflectionTestUtils.setField(snapshots, "service", scene);
        var access = new ArtResourceAccess();
        ReflectionTestUtils.setField(access, "platformTenant", 1L);
        var web = new WebProperties();
        var interceptor = new Dim3dPlatformConfiguration().dim3dPlatformAccess(web, access);
        var platformMvc = MockMvcBuilders.standaloneSetup(controller, snapshots)
                .setCustomHandlerMapping(() -> {
                    var mapping = new RequestMappingHandlerMapping();
                    mapping.setPathPrefixes(Map.of(web.getAdminApi().getPrefix(), type -> true));
                    return mapping;
                })
                .addMappedInterceptors(interceptor.getPathPatterns(), interceptor.getInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler("dimensions-test", null)).build();
        when(scene.templatePageCombined(null, null, null, null, null, null, null, null, null))
                .thenReturn(new PageResult<>(List.of(Map.of("recordKind", "SNAPSHOT")), 1L));
        when(scene.templateInfo("42")).thenReturn(Map.of("templateSceneId", "9007199254740994"));
        String operation = "/admin-api/dim3d/operation";
        String tenant = "/admin-api/dim3d/tenant";

        try {
            login(2L);
            platformMvc.perform(get(tenant + "/template/page").param("includeSnapshots", "true"))
                    .andExpect(jsonPath("$.rows[0].recordKind").value("SNAPSHOT"));
            platformMvc.perform(get(tenant + "/scene/template/info/42"))
                    .andExpect(jsonPath("$.data.templateSceneId").value("9007199254740994"));
            platformMvc.perform(put(operation + "/template/edit/42").header("tenant-id", "1")
                            .contentType("application/json").content(BODY))
                    .andExpect(jsonPath("$.code").value(403));
            platformMvc.perform(post(operation + "/scene/template/publish/42").param("versionNo", "1"))
                    .andExpect(jsonPath("$.code").value(403));
            login(1L);
            platformMvc.perform(post(operation + "/template/add").contentType("application/json").content(BODY))
                    .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
            platformMvc.perform(delete(tenant + "/template/remove/42")).andExpect(jsonPath("$.code").value(403));
            platformMvc.perform(get("/admin-api/dim3d/template/page").param("includeSnapshots", "true"))
                    .andExpect(jsonPath("$.total").value(1));
        } finally {
            SecurityContextHolder.clearContext();
            TenantContextHolder.clear();
        }
        verify(catalog, never()).updateTemplate(anyLong(), any());
        verify(catalog, never()).deleteTemplate(anyLong());
        verify(scene, never()).publishTemplate(anyString(), anyInt(), any());
    }

    private static void login(long tenant) {
        var user = new LoginUser();
        user.setId(10L);
        user.setTenantId(tenant);
        TenantContextHolder.setTenantId(tenant);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void fileTemplatePageReturnsRootRowsAndExactIdentifiers() throws Exception {
        var template = new Dim3dTemplate();
        template.setId(9007199254740993L);
        template.setTemplateFileId(9007199254740994L);
        template.setTenantId("41");
        when(catalog.getTemplatePage(null, "INDUSTRY", null, null, null, 1, 10, null, null))
                .thenReturn(new PageResult<>(List.of(template), 1L));

        mvc.perform(get("/dim3d/template/page").param("templateType", "INDUSTRY").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.rows[0].templateFileId").value("9007199254740994"))
                .andExpect(jsonPath("$.rows[0].tenantId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath());
    }

    /** 目录维护回执沿用契约的空成功数据，关联编号通过详情及分页查询。 */
    @Test
    void fileTemplateMutationsReturnEmptySuccess() throws Exception {
        when(catalog.createTemplate(any())).thenReturn(9007199254740993L);
        mvc.perform(post("/dim3d/template/add").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(put("/dim3d/template/edit/42").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(put("/dim3d/template/OFFLINE/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(delete("/dim3d/template/remove/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        verify(catalog).updateTemplate(eq(42L), argThat(template -> template.getTemplateFileId().equals(9007199254740994L)));
    }

    /** 管理页用快照编号及来源区分详情和维护路由，合并列表须持续返回。 */
    @Test
    void combinedPageKeepsSnapshotIdentityAndPassesSorting() throws Exception {
        when(scene.templatePageCombined(null, null, null, null, null, 1, 10, "templateName", "asc"))
                .thenReturn(new PageResult<>(List.of(Map.<String, Object>of("id", "42",
                        "templateSceneId", "9007199254740994", "recordKind", "SNAPSHOT")), 1L));
        mvc.perform(get("/dim3d/template/page").param("includeSnapshots", "true").param("pageNum", "1")
                        .param("pageSize", "10").param("orderByColumn", "templateName").param("isAsc", "asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].recordKind").value("SNAPSHOT"))
                .andExpect(jsonPath("$.rows[0].templateSceneId").value("9007199254740994"));
        verifyNoInteractions(catalog);
    }

    /** 可信状态及租户由服务端决定，客户端至少要提交契约要求的类型和归属。 */
    @Test
    void requiresExplicitTypeAndKeepsUntrustedFieldsOutOfWrites() throws Exception {
        mvc.perform(post("/dim3d/template/add").contentType("application/json")
                        .content(BODY.replace("\"templateType\":\"INDUSTRY\",", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalog);
        mvc.perform(put("/dim3d/template/edit/42").contentType("application/json")
                        .content(BODY.replace("}", ",\"id\":9,\"tenantId\":\"62\",\"createBy\":7,\"delFlag\":\"1\"}")))
                .andExpect(status().isOk());
        verify(catalog).updateTemplate(eq(42L), argThat(template -> template.getId() == null
                && template.getTenantId() == null && template.getCreateBy() == null && template.getDelFlag() == null));
    }
}
