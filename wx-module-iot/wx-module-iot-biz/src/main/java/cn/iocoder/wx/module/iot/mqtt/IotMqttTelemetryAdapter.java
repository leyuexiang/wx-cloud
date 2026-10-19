package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.service.IotConnectorService;
import cn.iocoder.wx.module.iot.service.IotTelemetryService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Validator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;

/** MQTT 报文只携带外部身份，认证来源租户和内部绑定来自受控配置。 */
public class IotMqttTelemetryAdapter {
    private final IotConnectorService connectors;
    private final TenantFrameworkService tenants;
    private final IotTelemetryService telemetry;
    private final Validator validator;
    private final ObjectReader reader;
    private final ObjectReader treeReader;

    public IotMqttTelemetryAdapter(IotConnectorService connectors, TenantFrameworkService tenants,
            IotTelemetryService telemetry, Validator validator, ObjectMapper mapper) {
        this.connectors = connectors;
        this.tenants = tenants;
        this.telemetry = telemetry;
        this.validator = validator;
        reader = mapper.readerFor(TwinTelemetryIngestRequest.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        treeReader = mapper.readerFor(JsonNode.class).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public TwinTelemetryEvent ingest(Long tenantId, String connectorId, IotMqttSettings settings,
                                     String topic, byte[] payload) {
        if (!settings.topics().contains(topic) || payload == null || payload.length == 0 || payload.length > 65536) {
            throw new IllegalArgumentException("MQTT 主题或报文长度无效");
        }
        TwinTelemetryIngestRequest request;
        try {
            // JSON 时间必须为明确的 ISO 字符串，避免全局秒/毫秒配置误解外部整数时间戳。
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString();
            JsonNode root = treeReader.readValue(text);
            if (root == null || !root.isObject() || !root.path("eventTime").isTextual()) {
                throw new IllegalArgumentException("MQTT 事件时间必须为 ISO 字符串");
            }
            request = reader.readValue(root);
        } catch (IOException ex) {
            throw new IllegalArgumentException("MQTT JSON 报文无法解析");
        }
        if (request == null) {
            throw new IllegalArgumentException("MQTT 报文不能为空");
        }
        request.setConnectorId(connectorId);
        request.setTenantId(tenantId);
        request.setReplay(false);
        // 外部身份无法覆盖绑定主数据，包括绑定中尚未设置的兼容兜底字段。
        request.setDeviceId(null);
        request.setSceneId(null);
        request.setObjectId(null);
        request.setMappingRevision(null);
        if (!validator.validate(request).isEmpty() || request.getEventTime() == null
                || request.getTraceId() == null || request.getTraceId().isBlank()) {
            throw new IllegalArgumentException("MQTT 报文缺少稳定时间、追踪号或必要点位字段");
        }
        return TenantUtils.execute(tenantId, () -> {
            tenants.validTenant(tenantId);
            IotConnectorDO current = connectors.getConnector(connectorId);
            if (current == null || !Boolean.TRUE.equals(current.getEnabled())
                    || !"MQTT".equals(current.getConnectorType()) || !settings.matches(current)) {
                throw new IllegalStateException("MQTT 连接器已停用、删除或配置已变化");
            }
            return telemetry.ingestBound(request);
        });
    }
}
