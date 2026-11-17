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
        mvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
    }

    @AfterEach
    void release() { validator.close(); }

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
                .andExpect(jsonPath("$.rows[0].priceYuan").value(123.45))
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

    @Test
    void detailEditAndRemoveUseContractMethodsAndPathIds() throws Exception {
        var model = new Dim3dModel();
        model.setModelFileUrl("https://example.invalid/model.glb");
        when(catalog.getModel(7L)).thenReturn(model);
        mvc.perform(get("/dim3d/model/info/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelFileUrl").value("https://example.invalid/model.glb"));
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
                    () -> controller.editModel(7L, body));
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
            assertThrows(jakarta.validation.ConstraintViolationException.class, () -> controller.editModel(7L, body));
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
                .andExpect(jsonPath("$.data.resourceRelativePath").value("models/tree.glb"));
        verify(catalog).getModelResource(101L);
    }
}
