package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.mqtt.IotMqttConnectivityTester;
import cn.iocoder.wx.module.iot.mqtt.IotMqttSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.CONNECTOR_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 连接测试严格使用当前租户保存配置，并把正式运行租约留给动态管理器。 */
class IotConnectorConnectivityServiceTest {
    private final IotConnectorMapper mapper = mock(IotConnectorMapper.class);
    private final IotMqttConnectivityTester tester = mock(IotMqttConnectivityTester.class);
    private final IotConnectorConnectivityService service = new IotConnectorConnectivityService(mapper,
            new IotConnectorConfigValidator(new ObjectMapper(), false), tester);
    private IotConnectorDO connector;

    @BeforeEach
    void prepare() {
        TenantContextHolder.setTenantId(7L);
        connector = new IotConnectorDO();
        connector.setId(8L);
        connector.setTenantId(7L);
        connector.setConnectorId("pilot");
        connector.setConnectorType("MQTT");
        connector.setVersion(2);
        connector.setEndpoint("ssl://broker.example.com:8883");
        connector.setCredentialRef("IOT_MQTT_PILOT");
        connector.setConfigJson("{\"topics\":[\"pilot/a\"],\"qos\":1}");
        when(mapper.selectById(8L)).thenReturn(connector);
        when(tester.test(any(IotMqttSettings.class), any(Duration.class)))
                .thenReturn(new IotConnectorConnectionTestRespVO("pilot",
                        IotConnectorConnectionTestRespVO.Outcome.SUCCEEDED, 12, 1, "连接和主题订阅成功"));
    }

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void shouldPassParsedSavedSettingsToIsolatedTester() {
        var request = new IotConnectorConnectionTestReqVO();
        request.setId(8L);
        request.setTimeoutSeconds(4);

        var result = service.test(request);

        assertEquals(IotConnectorConnectionTestRespVO.Outcome.SUCCEEDED, result.outcome());
        verify(tester).test(argThat(settings -> settings.id().equals(8L)
                && settings.version().equals(2) && settings.topics().equals(java.util.List.of("pilot/a"))),
                eq(Duration.ofSeconds(4)));
        verify(mapper).selectById(8L);
    }

    @Test
    void shouldNotCreateProbeForNonMqttConnector() {
        connector.setConnectorType("HTTPS");
        var request = new IotConnectorConnectionTestReqVO();
        request.setId(8L);

        var result = service.test(request);

        assertEquals(IotConnectorConnectionTestRespVO.Outcome.UNSUPPORTED_PROTOCOL, result.outcome());
        verifyNoInteractions(tester);
    }

    @Test
    void shouldUseNotExistsForMissingTenantScopedConnector() {
        when(mapper.selectById(8L)).thenReturn(null);
        var request = new IotConnectorConnectionTestReqVO();
        request.setId(8L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.test(request));

        assertEquals(CONNECTOR_NOT_EXISTS.getCode(), error.getCode());
        verifyNoInteractions(tester);
    }
}
