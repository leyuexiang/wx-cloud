package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dResourceController;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import cn.iocoder.wx.framework.security.core.LoginUser;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.web.config.WebProperties;
import cn.iocoder.wx.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.wx.module.dimensions.config.ModelPlatformConfiguration;
import cn.iocoder.wx.module.dimensions.controller.admin.ModelImportController;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.Map;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 覆盖目录五类接口的实际控制器分派，网关前缀及真实鉴权由部署验收另行核对。 */
class Dim3dModelControllerContractTest {
    private Dim3dResourceCatalogService catalog;
    private Dim3dResourceController controller;
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;
    private final ObjectMapper json = new ObjectMapper();
    private static final String BODY = "{\"modelName\":\"测试模型\",\"category\":\"TREE\","
            + "\"resourceBelong\":\"MINE\",\"isPaid\":\"FREE\",\"modelFileId\":\"101\"}";

    @BeforeEach
    void setup() {
        catalog = mock(Dim3dResourceCatalogService.class);
        controller = new Dim3dResourceController();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ReflectionTestUtils.setField(controller, "service", catalog);
        ReflectionTestUtils.setField(controller, "json", json);
        ReflectionTestUtils.setField(controller, "validator", validator);
        var responses = new Dim3dCatalogResponseAssembler();
        var users = mock(cn.iocoder.wx.module.system.api.user.AdminUserApi.class);
        var administrator = new cn.iocoder.wx.module.system.api.user.dto.AdminUserRespDTO();
        administrator.setId(1L);
        administrator.setNickname("超级管理员");
        when(users.getUserMap(anyCollection())).thenReturn(java.util.Map.of(1L, administrator));
        ReflectionTestUtils.setField(responses, "users", users);
        ReflectionTestUtils.setField(controller, "catalogResponses", responses);
        mvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    @AfterEach
    void release() {
        validator.close();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    /** 真实控制器与端别拦截器共同验证请求分派，伪造的请求头无法改变令牌身份。 */
    @Test
    void routesBothPlatformsAndRejectsCrossPlatformReadsAndWrites() throws Exception {
        var access = new ArtResourceAccess();
        ReflectionTestUtils.setField(access, "platformTenant", 1L);
        var web = new WebProperties();
        var interceptor = new ModelPlatformConfiguration().modelPlatformAccess(web, access);
        var imports = new ModelImportController();
        var platformMvc = MockMvcBuilders.standaloneSetup(controller, imports)
                .setCustomHandlerMapping(() -> {
                    var mapping = new RequestMappingHandlerMapping();
                    mapping.setPathPrefixes(Map.of(web.getAdminApi().getPrefix(), type -> true));
                    return mapping;
                })
                .addMappedInterceptors(interceptor.getPathPatterns(), interceptor.getInterceptor())
                .setControllerAdvice(new GlobalExceptionHandler("dimensions-test", null))
                .setValidator(validator).build();
        when(catalog.getModelPage(null, null, null, null, null, null, null, null, null))
                .thenReturn(new PageResult<>(List.of(), 0L));
        String operationPath = "/admin-api/dim3d/operation";
        String tenantPath = "/admin-api/dim3d/tenant";

        login(2L);
        platformMvc.perform(get(tenantPath + "/model/page")).andExpect(jsonPath("$.code").value(200));
        platformMvc.perform(get(operationPath + "/model/page").header("tenant-id", "1"))
                .andExpect(jsonPath("$.code").value(403));
        platformMvc.perform(post(operationPath + "/model-imports").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(403));
        platformMvc.perform(get("/admin-api/dim3d/model/page")).andExpect(jsonPath("$.code").value(200));

        login(1L);
        platformMvc.perform(get(operationPath + "/model/page")).andExpect(jsonPath("$.code").value(200));
        platformMvc.perform(put(tenantPath + "/model/OFFLINE/7")).andExpect(jsonPath("$.code").value(403));
        platformMvc.perform(get(tenantPath + "/resource/info/7")).andExpect(jsonPath("$.code").value(403));
        verify(catalog, never()).changeModelStatus(anyLong(), anyString());
        verify(catalog, never()).getModelResource(anyLong());
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
    void pageProvidesContractRowsWithoutAdditionalEnvelope() throws Exception {
        var model = new Dim3dModel();
        model.setId(9007199254740993L);
        model.setPrice(12345);
        var page = new PageResult<>(List.of(model), 1L);
        when(catalog.getModelPage(null, "MINE", null, null, null, 1, 10)).thenReturn(page);
        when(catalog.getModelPage(null, "MINE", null, null, null, 1, 10, "price", "asc")).thenReturn(page);
        mvc.perform(get("/dim3d/model/page").param("resourceBelong", "MINE").param("pageNum", "1")
                        .param("pageSize", "10").param("orderByColumn", "price").param("isAsc", "asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.rows[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.rows[0].priceYuan").value("123.45"))
                .andExpect(jsonPath("$.rows[0].resourceId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(catalog).getModelPage(null, "MINE", null, null, null, 1, 10, "price", "asc");
    }

    @Test
    void canonicalAddReturnsEmptySuccess() throws Exception {
        when(catalog.createModel(any())).thenReturn(9007199254740993L);
        mvc.perform(post("/dim3d/model/add").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /** 替换仍走目录编辑，查询参数仅用于绑定本次上传操作。 */
    @Test
    void replacementUsesDirectoryEditWithOperationAndEmptyReceipt() throws Exception {
        mvc.perform(put("/dim3d/model/edit/7").param("operationId", "replacement")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
        verify(catalog).replaceManagedModel(eq(7L), argThat(model -> model.getModelFileId().equals(101L)), eq("replacement"));
    }

    @Test
    void detailEditAndRemoveUseContractMethodsAndPathIds() throws Exception {
        var model = new Dim3dModel();
        model.setModelFileId(101L);
        model.setModelFileUrl("https://example.invalid/model.glb");
        when(catalog.getModel(7L)).thenReturn(model);
        mvc.perform(get("/dim3d/model/info/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelFileId").value("101"))
                .andExpect(jsonPath("$.data.modelFileUrl").doesNotExist());
        mvc.perform(put("/dim3d/model/edit/7").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(delete("/dim3d/model/remove/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
        verify(catalog).updateModel(eq(7L), argThat(body -> body.getModelFileId().equals(101L)));
        verify(catalog).deleteModel(7L);
    }

    /** 金额必须保留整数分语义，旧请求也不能被反序列化悄悄截断。 */
    @Test
    void legacyAddRejectsFractionalPriceBeforeWriting() throws Exception {
        var body = json.readTree(BODY.replace("\"FREE\"", "\"PAID\"").replace("}", ",\"price\":1.01}"));
        assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class, () -> controller.addModel(body));
        verifyNoInteractions(catalog);
    }

    @Test
    void editRejectsFractionalAndOverflowPriceBeforeWriting() throws Exception {
        for (String amount : List.of("1.01", "2147483648", "-1")) {
            var body = json.readTree(BODY.replace("}", ",\"price\":" + amount + "}"));
            assertThrows(cn.iocoder.wx.framework.common.exception.ServiceException.class,
                    () -> controller.editModel(7L, body, null));
        }
        verifyNoInteractions(catalog);
    }

    @Test
    void missingLegacyModelFileIdIsRejectedBeforeWriting() throws Exception {
        var body = json.readTree(BODY.replace(",\"modelFileId\":\"101\"", ""));
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> controller.addModel(body));
        verifyNoInteractions(catalog);
    }

    /** 契约要求显式选择归属和付费状态，不能用实体默认值补齐缺失输入。 */
    @Test
    void belongAndPaidStateAreRequiredForBothWriteRoutes() throws Exception {
        for (String field : List.of("resourceBelong", "isPaid")) {
            var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(BODY);
            body.remove(field);
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> controller.addModel(body));
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> controller.editModel(7L, body, null));
        }
        verifyNoInteractions(catalog);
    }

    @Test
    void resourceInfoUsesDocumentRouteAndStringFileIdentifier() throws Exception {
        when(catalog.getModelResource(101L)).thenReturn(java.util.Map.of("id", "101", "resourceType", "MODEL",
                "resourceRelativePath", "models/tree.glb"));
        mvc.perform(get("/dim3d/resource/info/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.resourceType").value("MODEL"))
                .andExpect(jsonPath("$.data.resourceRelativePath").value("models/tree.glb"))
                // 路径契约收敛为相对路径，资源详情不再同时返回签名地址和有效期。
                .andExpect(jsonPath("$.data.modelFileUrl").doesNotExist())
                .andExpect(jsonPath("$.data.downloadExpiresAt").doesNotExist());
        verify(catalog).getModelResource(101L);
    }

    /** 上下线沿用路径状态和目录编号，按文档返回空数据，避免布尔值响应差异。 */
    @Test
    void modelStatusUsesContractRouteAndEmptySuccess() throws Exception {
        mvc.perform(put("/dim3d/model/OFFLINE/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(put("/dim3d/model/ONLINE/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(catalog).changeModelStatus(7L, "OFFLINE");
        verify(catalog).changeModelStatus(7L, "ONLINE");
    }

    /** 对外响应严格匹配参考字段，持久化实体内部的关联、租户和预览字段不能随之输出。 */
    @Test
    void detailUsesOnlyReferenceFieldsAndStringFormats() throws Exception {
        var model = new Dim3dModel();
        model.setId(26L);
        model.setModelName("测试模型");
        model.setResourceId(9007199254740993L);
        model.setTenantId("1");
        model.setDataUserId(1L);
        model.setIsPaid("PAID");
        model.setPrice(100);
        model.setCreateBy(1L);
        model.setUpdateBy(1L);
        model.setCreateTime(java.time.LocalDateTime.of(2026, 11, 5, 19, 49, 49));
        model.setUpdateTime(java.time.LocalDateTime.of(2026, 11, 5, 19, 50, 13));
        model.setModelFileUrl("https://example.invalid/model.glb");
        when(catalog.getModel(26L)).thenReturn(model);
        var result = mvc.perform(get("/dim3d/model/info/26")).andExpect(status().isOk()).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var fields = new java.util.HashSet<String>();
        data.fieldNames().forEachRemaining(fields::add);
        assertEquals(java.util.Set.of("id", "modelName", "modelDesc", "category", "resourceBelong", "isPaid",
                "priceYuan", "coverImageId", "modelFileId", "status", "createBy", "createByName", "createTime",
                "updateBy", "updateByName", "updateTime"), fields);
        assertEquals("9007199254740993", data.path("modelFileId").asText());
        assertTrue(data.path("priceYuan").isTextual());
        assertEquals("1.00", data.path("priceYuan").asText());
        assertTrue(data.path("createBy").isTextual());
        assertEquals("2026-11-05 19:49:49", data.path("createTime").asText());
        assertEquals("2026-11-05 19:50:13", data.path("updateTime").asText());
        assertEquals("超级管理员", data.path("createByName").asText());
        assertEquals("超级管理员", data.path("updateByName").asText());
    }
}
