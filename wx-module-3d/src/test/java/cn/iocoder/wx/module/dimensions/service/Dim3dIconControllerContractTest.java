package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dResourceController;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import cn.iocoder.wx.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 通过公开路由核对图标目录协议，避免直接序列化持久化实体。 */
class Dim3dIconControllerContractTest {
    private static final String BODY = """
            {"iconName":"设备","category":"DEVICE_ICON","resourceBelong":"MINE","iconFileId":"9007199254740994"}
            """;
    private Dim3dResourceCatalogService catalog;
    private AdminUserApi users;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        catalog = mock(Dim3dResourceCatalogService.class);
        users = mock(AdminUserApi.class);
        var controller = new Dim3dResourceController();
        var responses = new Dim3dCatalogResponseAssembler();
        ReflectionTestUtils.setField(responses, "users", users);
        ReflectionTestUtils.setField(controller, "service", catalog);
        ReflectionTestUtils.setField(controller, "catalogResponses", responses);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void detailKeepsFileIdentityAndHidesInternalOwnership() throws Exception {
        var icon = new Dim3dIcon();
        icon.setId(9007199254740993L);
        icon.setIconFileId(9007199254740994L);
        icon.setTenantId("41");
        icon.setResourceId(13L);
        when(catalog.getIcon(9007199254740993L)).thenReturn(icon);

        mvc.perform(get("/dim3d/icon/info/9007199254740993"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.iconFileId").value("9007199254740994"))
                .andExpect(jsonPath("$.data.tenantId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.data.resourceId").doesNotHaveJsonPath());
    }

    /** 同页重复审计账号只查一次，格式与详情相同，根级分页可直接供现有前端读取。 */
    @Test
    void pageUsesRootRowsAndBatchesAuditors() throws Exception {
        var icon = new Dim3dIcon();
        icon.setId(42L);
        icon.setCreateBy(7001L);
        icon.setUpdateBy(7001L);
        icon.setUpdateTime(LocalDateTime.of(2026, 11, 18, 9, 30));
        var user = new AdminUserRespDTO();
        user.setId(7001L);
        user.setNickname("管理员");
        when(users.getUserMap(List.of(7001L))).thenReturn(Map.of(7001L, user));
        when(catalog.getIconPage("设备", "DEVICE_ICON", "室内", "MINE", "ONLINE", 1, 10, "iconName", "asc"))
                .thenReturn(new PageResult<>(List.of(icon, icon), 2L));

        mvc.perform(get("/dim3d/icon/page").param("iconName", "设备").param("category", "DEVICE_ICON")
                        .param("secondaryCategory", "室内").param("resourceBelong", "MINE").param("status", "ONLINE")
                        .param("pageNum", "1").param("pageSize", "10").param("orderByColumn", "iconName").param("isAsc", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("42"))
                .andExpect(jsonPath("$.rows[0].createByName").value("管理员"))
                .andExpect(jsonPath("$.rows[1].updateTime").value("2026-11-18 09:30:00"))
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath());
        verify(users).getUserMap(List.of(7001L));
        verifyNoMoreInteractions(users);
    }

    /** 新增保留目录编号，其他维护入口使用空成功回执。 */
    @Test
    void writesUseDocumentedPathsAndSuccessEnvelopes() throws Exception {
        when(catalog.createIcon(any())).thenReturn(9007199254740993L);
        mvc.perform(post("/dim3d/icon/add").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("9007199254740993"));
        mvc.perform(put("/dim3d/icon/edit/42").contentType("application/json").content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(put("/dim3d/icon/OFFLINE/42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(delete("/dim3d/icon/remove/42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data").value(nullValue()));
        verify(catalog).updateIcon(eq(42L), argThat(icon -> icon.getIconFileId().equals(9007199254740994L)));
        verify(catalog).changeIconStatus(42L, "OFFLINE");
        verify(catalog).deleteIcon(42L);
    }

    /** 缺省归属不会隐式保存为系统资源，非整数文件编号不进入目录写入。 */
    @Test
    void writesRequireExplicitScopeAndDecimalFileId() throws Exception {
        mvc.perform(post("/dim3d/icon/add").contentType("application/json")
                        .content(BODY.replace("\"resourceBelong\":\"MINE\",", "")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/dim3d/icon/edit/42").contentType("application/json")
                        .content(BODY.replace("\"9007199254740994\"", "17.5")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(catalog);
    }
}
