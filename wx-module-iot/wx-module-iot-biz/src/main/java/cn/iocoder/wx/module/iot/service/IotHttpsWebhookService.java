package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.api.IotConnectorType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.api.TwinTelemetryIngestRequest;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.WEBHOOK_CONNECTOR_INVALID;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.WEBHOOK_PAYLOAD_INVALID;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.WEBHOOK_SIGNATURE_INVALID;

/** HTTPS 回调适配器：先验证连接器和报文签名，再交给统一遥测接入链路。 */
@Service
@RequiredArgsConstructor
public class IotHttpsWebhookService {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final IotConnectorService connectorService;
    private final TenantFrameworkService tenantFrameworkService;
    private final IotTelemetryService telemetryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TwinTelemetryEvent ingest(String connectorId, String timestamp, String signature, byte[] body) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        tenantFrameworkService.validTenant(tenantId);
        IotConnectorDO connector = connectorService.getConnector(connectorId);
        if (connector == null || !Boolean.TRUE.equals(connector.getEnabled())
                || !IotConnectorType.HTTPS.name().equals(connector.getConnectorType())) {
            throw exception(WEBHOOK_CONNECTOR_INVALID);
        }
        if (body == null || body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw exception(WEBHOOK_PAYLOAD_INVALID);
        }
        verifySignature(connector, tenantId, timestamp, signature, body);

        TwinTelemetryIngestRequest request;
        try {
            request = objectMapper.readValue(body, TwinTelemetryIngestRequest.class);
        } catch (IOException ex) {
            throw exception(WEBHOOK_PAYLOAD_INVALID);
        }
        // 回调必须带稳定事件时间和链路标识，否则重试会生成不同 eventId。
        if (request == null || !validator.validate(request).isEmpty() || request.getEventTime() == null
                || request.getTraceId() == null || request.getTraceId().isBlank()
                || !Objects.equals(connectorId, request.getConnectorId())) {
            throw exception(WEBHOOK_PAYLOAD_INVALID);
        }
        request.setTenantId(tenantId);
        request.setReplay(false);
        return telemetryService.ingest(request);
    }

    private void verifySignature(IotConnectorDO connector, Long tenantId, String timestamp,
                                 String signature, byte[] body) {
        String credentialRef = connector.getCredentialRef();
        if (credentialRef == null || !credentialRef.matches("IOT_WEBHOOK_[A-Z0-9_]+")) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        String secret = System.getenv(credentialRef);
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || timestamp == null || signature == null) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        long now = Instant.now().getEpochSecond();
        if (epochSeconds < now - MAX_CLOCK_SKEW_SECONDS || epochSeconds > now + MAX_CLOCK_SKEW_SECONDS) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        byte[] receivedSignature;
        try {
            receivedSignature = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException ex) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        if (receivedSignature.length != 32) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            // 租户和连接器也参与签名，阻止同一报文跨租户或跨连接器转投。
            byte[] prefix = (timestamp + "\n" + tenantId + "\n" + connector.getConnectorId() + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            mac.update(prefix);
            byte[] expectedSignature = mac.doFinal(body);
            if (!MessageDigest.isEqual(expectedSignature, receivedSignature)) {
                throw exception(WEBHOOK_SIGNATURE_INVALID);
            }
        } catch (GeneralSecurityException ex) {
            throw exception(WEBHOOK_SIGNATURE_INVALID);
        }
    }
}
