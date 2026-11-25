package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.util.json.databind.TimestampLocalDateTimeSerializer;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import cn.iocoder.wx.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 精确验证字段投影、参考格式及用户批量查询边界，避免再次泄露实体字段或逐行查询。 */
class Dim3dModelResponseAssemblerTest {
    private Dim3dCatalogResponseAssembler assembler;
    private AdminUserApi users;

    @BeforeEach
    void setup() {
        users = mock(AdminUserApi.class);
        assembler = new Dim3dCatalogResponseAssembler();
        ReflectionTestUtils.setField(assembler, "users", users);
    }

    @Test
    void keepsReferenceFormatsDespiteGlobalTimestampSerialization() {
        var model = model(1L, 2L);
        model.setId(26L);
        model.setResourceId(9007199254740993L);
        model.setModelFileId(101L);
        model.setCoverImageId(9007199254740994L);
        model.setPrice(0);
        model.setCreateTime(LocalDateTime.of(2026, 11, 5, 19, 49, 49));
        model.setUpdateTime(LocalDateTime.of(2026, 11, 5, 19, 50, 13));
        model.setTenantId("1");
        model.setDelFlag("0");
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(1L, user(1L, "创建人"), 2L, user(2L, "更新人")));
        var mapper = new ObjectMapper().registerModule(new SimpleModule()
                .addSerializer(LocalDateTime.class, TimestampLocalDateTimeSerializer.INSTANCE));
        var result = mapper.valueToTree(assembler.single(model));
        assertEquals(16, result.size());
        assertEquals("9007199254740993", result.path("modelFileId").asText());
        assertEquals("9007199254740994", result.path("coverImageId").asText());
        assertEquals("0.00", result.path("priceYuan").asText());
        assertEquals("1", result.path("createBy").asText());
        assertEquals("2", result.path("updateBy").asText());
        assertEquals("创建人", result.path("createByName").asText());
        assertEquals("更新人", result.path("updateByName").asText());
        assertEquals("2026-11-05 19:49:49", result.path("createTime").asText());
        assertEquals("2026-11-05 19:50:13", result.path("updateTime").asText());
        for (String field : List.of("resourceId", "tenantId", "delFlag", "modelFileUrl", "resourceRelativePath", "downloadExpiresAt", "price")) {
            assertFalse(result.has(field), field);
        }
    }

    @Test
    void deduplicatesCreatorsAndUpdatersAcrossPage() {
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(1L, user(1L, "创建人"), 2L, user(2L, "更新人")));
        var results = assembler.multiple(List.of(model(1L, 2L), model(1L, 1L), model(2L, 1L)));
        assertEquals(3, results.size());
        assertEquals("创建人", results.get(1).updateByName());
        verify(users).getUserMap(argThat(ids -> Set.copyOf(ids).equals(Set.of(1L, 2L)) && ids.size() == 2));
        verifyNoMoreInteractions(users);
    }

    @Test
    void boundsBatchSizeForMaximumPage() {
        List<Dim3dModel> page = new ArrayList<>();
        for (long index = 0; index < 200; index++) page.add(model(index * 2 + 1, index * 2 + 2));
        List<Integer> batchSizes = new ArrayList<>();
        when(users.getUserMap(anyCollection())).thenAnswer(call -> {
            Collection<?> ids = call.getArgument(0);
            batchSizes.add(ids.size());
            return Map.of();
        });
        assertEquals(200, assembler.multiple(page).size());
        assertEquals(List.of(200, 200), batchSizes);
        verify(users, times(2)).getUserMap(anyCollection());
    }

    @Test
    void emptyPageAndMissingAuditIdsSkipRemoteLookup() {
        assertTrue(assembler.multiple(List.of()).isEmpty());
        var result = assembler.single(new Dim3dModel());
        assertNull(result.createByName());
        assertNull(result.updateByName());
        assertNull(result.createTime());
        assertNull(result.priceYuan());
        verifyNoInteractions(users);
    }

    @Test
    void missingUsersRemainNullAndLegacyFileIdIsPreserved() {
        var model = model(99L, 99L);
        model.setModelFileId(101L);
        when(users.getUserMap(anyCollection())).thenReturn(Map.of());
        var result = assembler.single(model);
        assertEquals("101", result.modelFileId());
        assertNull(result.createByName());
        assertNull(result.updateByName());
        verify(users).getUserMap(List.of(99L));
    }

    /** 三维服务采用显式客户端清单，补充操作人名称必须同时登记系统用户客户端。 */
    @Test
    void rpcConfigurationRegistersBatchUserLookup() {
        var configuration = cn.iocoder.wx.module.dimensions.framework.rpc.config.RpcConfiguration.class
                .getAnnotation(org.springframework.cloud.openfeign.EnableFeignClients.class);
        assertTrue(java.util.Arrays.asList(configuration.clients()).contains(AdminUserApi.class));
    }

    private static Dim3dModel model(Long creator, Long updater) {
        var model = new Dim3dModel();
        model.setCreateBy(creator);
        model.setUpdateBy(updater);
        return model;
    }

    private static AdminUserRespDTO user(long id, String name) {
        var user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(name);
        return user;
    }
}
