package cn.iocoder.wx.module.system.controller.admin.dict;

import cn.iocoder.wx.framework.common.enums.CommonStatusEnum;
import cn.iocoder.wx.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.wx.module.system.dal.dataobject.dict.DictDataDO;
import cn.iocoder.wx.module.system.dal.mysql.dict.DictDataMapper;
import cn.iocoder.wx.module.system.service.dict.DictDataServiceImpl;
import cn.iocoder.wx.module.system.service.dict.DictTypeService;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 从真实请求入口验证字典读取合同，使用测试数据库和现有查询服务覆盖过滤与排序。
 * 启用真实方法授权检查；访问令牌解析与租户过滤仍需真实网关联调验收。
 */
@Import({DictDataServiceImpl.class, DictDataTypeControllerTest.ControllerConfiguration.class})
public class DictDataTypeControllerTest extends BaseDbUnitTest {

    /** 通过配置类扫描实际控制器，新路径不存在时必须得到真实的请求失败。 */
    @TestConfiguration
    @EnableMethodSecurity
    @ComponentScan(basePackages = "cn.iocoder.wx.module.system.controller.admin.dict",
            useDefaultFilters = false, includeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX, pattern = ".*\\.DictData(Type)?Controller"))
    static class ControllerConfiguration {
    }

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private DictDataMapper dictDataMapper;
    @MockBean
    private DictTypeService dictTypeService;

    private MockMvc mvc;

    @BeforeEach
    public void setUpMvc() {
        // 使用没有字典维护权限的已登录业务用户，避免测试依赖万能管理员角色。
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reader", "unused", List.of()));
        // 与生产配置保持相同的管理端前缀，校验用户提供的完整路径而非直接调用控制器方法。
        mvc = MockMvcBuilders.standaloneSetup(
                applicationContext.getBeansWithAnnotation(RestController.class).values().toArray())
                .setCustomHandlerMapping(() -> {
                    RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
                    mapping.setPathPrefixes(Map.of("/admin-api", type -> true));
                    return mapping;
                }).build();
    }

    @AfterEach
    public void clearAuthentication() {
        // 认证上下文属于线程资源，每例清理，避免匿名用例或其他测试继承当前身份。
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldReturnOnlyEnabledIconCategoriesInSortOrder() throws Exception {
        // 大编号必须以字符串回传；停用项和其他字典不可混入图标分类。
        dictDataMapper.insert(item(9007199254740993L, "dim3d_icon_category", "BUILDING", "建筑", 20, 0));
        dictDataMapper.insert(item(9007199254740994L, "dim3d_icon_category", "PLANT", "植物", 10, 0));
        dictDataMapper.insert(item(9007199254740995L, "dim3d_icon_category", "DISABLED", "停用", 1, 1));
        dictDataMapper.insert(item(9007199254740996L, "dim3d_model_category", "MODEL", "模型", 1, 0));

        mvc.perform(get("/admin-api/system/dict/data/type/dim3d_icon_category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].dictCode").value("9007199254740994"))
                .andExpect(jsonPath("$.data[0].dictValue").value("PLANT"))
                .andExpect(jsonPath("$.data[0].dictLabel").value("植物"))
                .andExpect(jsonPath("$.data[0].dictType").value("dim3d_icon_category"))
                .andExpect(jsonPath("$.data[1].dictValue").value("BUILDING"));
    }

    @Test
    public void shouldReturnEmptyArrayForMissingOrDisabledCategories() throws Exception {
        dictDataMapper.insert(item(1, "dim3d_icon_category", "DISABLED", "停用", 1, 1));
        dictDataMapper.insert(item(2, "dim3d_model_category", "MODEL", "模型", 1, 0));
        for (String type : List.of("dim3d_icon_category", "missing_category")) {
            mvc.perform(get("/admin-api/system/dict/data/type/{dictType}", type))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray()).andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dim3d_model_category", "dim3d_scene_category", "dim3d_access_mode",
            "dim3d_template_industry", "dim3d_template_exhibition", "dim3d_template_category"})
    public void shouldReadOtherCurrentFrontendDictionaries(String type) throws Exception {
        // 当前前端的其他字典共用此路径，不能为图标分类增加硬编码专用分支。
        dictDataMapper.insert(item(1, type, "BUSINESS_VALUE", "业务名称", 1, 0));
        mvc.perform(get("/admin-api/system/dict/data/type/{dictType}", type))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].dictType").value(type))
                .andExpect(jsonPath("$.data[0].dictValue").value("BUSINESS_VALUE"));
    }

    @Test
    public void shouldKeepExistingSimpleListFields() throws Exception {
        // 新适配器独立投影，不改变旧后台已使用的 label/value 响应。
        dictDataMapper.insert(item(1, "dim3d_icon_category", "ICON", "图标", 1, 0));
        mvc.perform(get("/admin-api/system/dict-data/list-all-simple"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].label").value("图标"))
                .andExpect(jsonPath("$.data[0].value").value("ICON"))
                .andExpect(jsonPath("$.data[0].dictLabel").doesNotExist());
    }

    @Test
    public void shouldRejectAnonymousDictionaryRead() {
        SecurityContextHolder.clearContext();
        // 独立请求环境没有生产异常过滤器，直接验证真实方法授权抛出的未登录异常。
        ServletException failure = assertThrows(ServletException.class,
                () -> mvc.perform(get("/admin-api/system/dict/data/type/dim3d_icon_category")));
        assertInstanceOf(AuthenticationCredentialsNotFoundException.class, failure.getCause());
    }

    private static DictDataDO item(long id, String type, String value, String label, int sort, int status) {
        // 测试值只进入每例清理的内存数据库，不作为正式分类初始化依据。
        return new DictDataDO().setId(id).setDictType(type).setValue(value).setLabel(label)
                .setSort(sort).setStatus(status == 0 ? CommonStatusEnum.ENABLE.getStatus()
                        : CommonStatusEnum.DISABLE.getStatus());
    }
}
