package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.service.IotConnectorService;
import cn.iocoder.wx.module.iot.service.IotTelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 外部 MQTT 报文不得修改租户或绑定主数据，重试数据保持时间和数值精度。 */
class IotMqttTelemetryAdapterTest {
    private final IotConnectorService connectors = mock(IotConnectorService.class);
    private final TenantFrameworkService tenants = mock(TenantFrameworkService.class);
    private final IotTelemetryService telemetry = mock(IotTelemetryService.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final IotMqttTelemetryAdapter adapter = new IotMqttTelemetryAdapter(connectors, tenants, telemetry,
            validatorFactory.getValidator(), mapper);
    private final IotMqttSettings settings = IotMqttSettings.parse(IotMqttSettingsTest.connector(), false, mapper);

    /** 每个用例结束清理线程变量与校验工厂，避免污染其他接入测试。 */
    @AfterEach
    void clean() {
        TenantContextHolder.clear();
        validatorFactory.close();
    }

    private byte[] payload(String extra) {
        return ("{\"externalDeviceId\":\"sensor-1\",\"pointCode\":\"temp\",\"value\":26.123456789123456789,"
                + "\"valueType\":\"NUMBER\",\"eventTime\":\"2026-10-19T00:00:00.123456789Z\",\"traceId\":\"seq-1\""
                + extra + "}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shouldUseDeploymentTenantAndBoundIdentityThenRestoreOuterContext() {
        TenantContextHolder.setTenantId(99L);
        when(connectors.getConnector("pilot")).thenAnswer(invocation -> {
            assertEquals(7L, TenantContextHolder.getTenantId());
            return IotMqttSettingsTest.connector();
        });
        when(telemetry.ingestBound(any())).thenAnswer(invocation -> {
            TwinTelemetryIngestRequest request = invocation.getArgument(0);
            assertEquals(7L, request.getTenantId());
            assertEquals("pilot", request.getConnectorId());
            assertEquals(new BigDecimal("26.123456789123456789"), request.getValue());
            assertEquals(Instant.parse("2026-10-19T00:00:00.123456789Z"), request.getEventTime());
            assertNull(request.getDeviceId());
            assertNull(request.getSceneId());
            assertNull(request.getObjectId());
            assertNull(request.getMappingRevision());
            assertFalse(request.getReplay());
            return null;
        });
        adapter.ingest(7L, "pilot", settings, "pilot/telemetry", payload(
                ",\"tenantId\":999,\"connectorId\":\"other\",\"deviceId\":\"forged\",\"sceneId\":\"forged\",\"replay\":true"));
        verify(telemetry).ingestBound(any());
        verify(tenants).validTenant(7L);
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void shouldRejectDisabledOrReconfiguredConnectorBeforeIngest() {
        var connector = IotMqttSettingsTest.connector();
        connector.setEnabled(false);
        when(connectors.getConnector("pilot")).thenReturn(connector);
        assertThrows(RuntimeException.class, () -> adapter.ingest(7L, "pilot", settings, "pilot/telemetry", payload("")));
        connector.setEnabled(true);
        connector.setConfigJson("{}");
        assertThrows(RuntimeException.class, () -> adapter.ingest(7L, "pilot", settings, "pilot/telemetry", payload("")));
        verifyNoInteractions(telemetry);
    }

    @Test
    void shouldRejectUnknownTopicOversizeAndUnstableMessageBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> adapter.ingest(7L, "pilot", settings, "other", payload("")));
        assertThrows(IllegalArgumentException.class, () -> adapter.ingest(7L, "pilot", settings, "pilot/telemetry", new byte[65537]));
        assertThrows(IllegalArgumentException.class, () -> adapter.ingest(7L, "pilot", settings, "pilot/telemetry",
                "{\"eventTime\":1234}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> adapter.ingest(7L, "pilot", settings, "pilot/telemetry",
                new String(payload(""), StandardCharsets.UTF_8).replace("seq-1", " ").getBytes(StandardCharsets.UTF_8)));
        verifyNoInteractions(connectors, telemetry, tenants);
    }

}
