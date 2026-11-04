package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRuntimeRespVO.RuntimeState;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorRuntimeDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 运行观测依赖数据库同次快照，测试替身不替代真实租约及数据库兼容验收。 */
class IotConnectorManagementTest {
    private final IotConnectorMapper mapper = mock(IotConnectorMapper.class);
    private final IotConnectorManagementService service = new IotConnectorManagementService(mapper,
            new IotConnectorConfigValidator(new ObjectMapper(), false));
    private IotConnectorRuntimeDO row;

    @BeforeEach
    void prepare() {
        TenantContextHolder.setTenantId(7L);
        row = new IotConnectorRuntimeDO();
        row.setId(1L);
        row.setConnectorId("pilot");
        row.setConnectorType("MQTT");
        row.setEnabled(true);
        row.setVersion(4);
        row.setStatus("CONNECTED");
        row.setOwnerPresent(true);
        row.setLeaseActive(true);
        row.setObservedAt(LocalDateTime.of(2026, 10, 21, 9, 0));
        row.setRuntimeLeaseUntil(row.getObservedAt().plusSeconds(45));
        when(mapper.selectRuntime(7L, 1L)).thenReturn(row);
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @CsvSource({"CONNECTED,CONNECTED", "DISCONNECTED,DISCONNECTED", "AUTH_FAILED,AUTH_FAILED",
            "ERROR,ERROR", "DISABLED,UNKNOWN", "legacy,UNKNOWN"})
    void shouldOnlyTrustKnownStatusUnderActiveDatabaseLease(String persisted, RuntimeState expected) {
        row.setStatus(persisted);
        var result = service.getRuntime(1L);
        assertEquals(expected, result.getEffectiveStatus());
        assertEquals(persisted, result.getPersistedStatus());
        assertEquals(row.getObservedAt(), result.getObservedAt());
        assertEquals(row.getRuntimeLeaseUntil(), result.getRuntimeLeaseUntil());
        assertEquals(4, result.getVersion());
        verify(mapper).selectRuntime(7L, 1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldOverrideHistoricalConnectedWhenLeaseExpired() {
        row.setLeaseActive(false);
        var result = service.getRuntime(1L);
        assertEquals(RuntimeState.LEASE_EXPIRED, result.getEffectiveStatus());
        assertEquals("CONNECTED", result.getPersistedStatus());
        assertFalse(result.isLeaseActive());
    }

    @Test
    void shouldNotTreatSingleModeWithoutOwnerAsRealtimeConnected() {
        row.setOwnerPresent(false);
        row.setLeaseActive(false);
        assertEquals(RuntimeState.UNOBSERVED, service.getRuntime(1L).getEffectiveStatus());
    }

    @Test
    void shouldPreferDisabledIntentOverOutstandingLease() {
        row.setEnabled(false);
        assertEquals(RuntimeState.DISABLED, service.getRuntime(1L).getEffectiveStatus());
    }

    @Test
    void shouldNotApplyMqttLeaseToWebhookOrMock() {
        row.setConnectorType("HTTPS");
        assertEquals(RuntimeState.NOT_APPLICABLE, service.getRuntime(1L).getEffectiveStatus());
    }

    @Test
    void shouldHandleMissingHistoricalStatusWithoutFailingQuery() {
        row.setStatus(null);
        assertEquals(RuntimeState.UNKNOWN, service.getRuntime(1L).getEffectiveStatus());
    }

    @Test
    void shouldRequireTenantAndNeverFallbackToCrossTenantLookup() {
        TenantContextHolder.clear();
        assertThrows(NullPointerException.class, () -> service.getRuntime(1L));
        assertThrows(NullPointerException.class, () -> service.getPage(new IotConnectorPageReqVO()));
        verifyNoInteractions(mapper);
        TenantContextHolder.setTenantId(8L);
        var error = assertThrows(ServiceException.class, () -> service.getRuntime(1L));
        assertEquals(CONNECTOR_NOT_EXISTS.getCode(), error.getCode());
        verify(mapper).selectRuntime(8L, 1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldNotSerializeOwnershipTokenOrConfiguration() {
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(service.getRuntime(1L));
        assertFalse(json.has("runtimeOwner"));
        assertFalse(json.has("ownerPresent"));
        assertFalse(json.has("credentialRef"));
        assertFalse(json.has("configJson"));
        assertFalse(json.has("endpoint"));
        assertFalse(json.has("runtimeVersion"));
    }

    private IotMqttConfigValidateReqVO draft() {
        var request = new IotMqttConfigValidateReqVO();
        request.setEndpoint("ssl://broker.example.com:8883");
        request.setCredentialRef("IOT_MQTT_NOT_INJECTED");
        request.setConfigJson("{\"topics\":[\"pilot/a\",\"pilot/a\",\"pilot/b\"],\"qos\":1}");
        return request;
    }

    @Test
    void shouldValidateUnsavedDraftWithoutDatabaseOrCredentials() {
        var result = service.validateMqtt(draft());
        assertEquals("MQTT_STRUCTURE", result.scope());
        assertEquals(2, result.topicCount());
        assertEquals(1, result.qos());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldUseDeploymentTlsPolicyAndRedactInvalidDraft() {
        var draft = draft();
        draft.setEndpoint("tcp://private-user:private-secret@broker.example.com:1883");
        var error = assertThrows(ServiceException.class, () -> service.validateMqtt(draft));
        assertEquals(CONNECTOR_CONFIG_INVALID.getCode(), error.getCode());
        assertFalse(error.getMessage().contains("private-secret"));
        draft.setEndpoint("tcp://broker.example.com:1883");
        assertThrows(ServiceException.class, () -> service.validateMqtt(draft));
        var development = new IotConnectorManagementService(mapper,
                new IotConnectorConfigValidator(new ObjectMapper(), true));
        assertDoesNotThrow(() -> development.validateMqtt(draft));
        verifyNoInteractions(mapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldApplyPageAndAllFiltersInOneTenantScopedDatabaseQuery() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "management-test"), IotConnectorDO.class);
        var request = new IotConnectorPageReqVO();
        request.setPageNo(3);
        request.setPageSize(20);
        request.setConnectorId("pilot");
        request.setConnectorType("MQTT");
        request.setEnabled(false);
        var page = new PageResult<IotConnectorDO>(List.of(), 43L);
        doCallRealMethod().when(mapper).selectManagementPage(7L, request);
        doReturn(page).when(mapper).selectPage(eq(request), any(Wrapper.class));
        assertSame(page, service.getPage(request));
        ArgumentCaptor<Wrapper<IotConnectorDO>> filter = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(eq(request), filter.capture());
        String sql = filter.getValue().getSqlSegment();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("connector_id LIKE"));
        assertTrue(sql.contains("connector_type"));
        assertTrue(sql.contains("enabled"));
        assertTrue(sql.contains("ORDER BY id DESC"));
        verify(mapper, never()).selectList(any(Wrapper.class));
    }
}
