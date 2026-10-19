package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** 只解释已约定的配置字段；凭据解析独立完成，避免进入配置指纹和日志。 */
public record IotMqttSettings(String endpoint, String credentialRef, String configJson, List<String> topics,
                              String clientId) {
    public static IotMqttSettings parse(IotConnectorDO connector, boolean allowInsecure, ObjectMapper mapper) {
        try {
            URI uri = URI.create(connector.getEndpoint());
            if (!("ssl".equals(uri.getScheme()) || allowInsecure && "tcp".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() != null && !uri.getPath().isEmpty()) {
                throw new IllegalArgumentException();
            }
            JsonNode config = mapper.readTree(connector.getConfigJson());
            if (config == null || !config.isObject()) {
                throw new IllegalArgumentException();
            }
            var names = config.fieldNames();
            while (names.hasNext()) {
                if (!List.of("topics", "qos").contains(names.next())) {
                    throw new IllegalArgumentException();
                }
            }
            JsonNode topicsNode = config.path("topics");
            if (!config.isObject() || !topicsNode.isArray() || topicsNode.isEmpty() || topicsNode.size() > 32
                    || !config.path("qos").isIntegralNumber() || !config.path("qos").canConvertToInt()
                    || config.path("qos").intValue() != 1) {
                throw new IllegalArgumentException();
            }
            LinkedHashSet<String> topics = new LinkedHashSet<>();
            for (JsonNode topicNode : topicsNode) {
                if (!topicNode.isTextual() || topicNode.textValue().isBlank()
                        || topicNode.textValue().getBytes(StandardCharsets.UTF_8).length > 512
                        || topicNode.textValue().indexOf('+') >= 0 || topicNode.textValue().indexOf('#') >= 0
                        || topicNode.textValue().indexOf('\0') >= 0) {
                    throw new IllegalArgumentException();
                }
                topics.add(topicNode.textValue());
            }
            String reference = connector.getCredentialRef();
            if (reference == null || !reference.matches("IOT_MQTT_[A-Z0-9_]+")) {
                throw new IllegalArgumentException();
            }
            String fingerprint = connector.getTenantId() + "\n" + connector.getConnectorId() + "\n"
                    + connector.getEndpoint() + "\n" + reference + "\n" + connector.getConfigJson();
            // 固定配置使用固定客户端号，进程重启继续 Broker 会话，配置变化隔离旧订阅。
            String clientId = "wx-iot-" + UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8));
            return new IotMqttSettings(connector.getEndpoint(), reference, connector.getConfigJson(),
                    List.copyOf(topics), clientId);
        } catch (Exception ex) {
            // URI 和 JSON 异常可能携带认证材料，不向上附带原配置或异常原文。
            throw new IllegalArgumentException("MQTT 地址、主题、QoS 或凭据引用配置无效");
        }
    }

    public boolean matches(IotConnectorDO connector) {
        return Objects.equals(endpoint, connector.getEndpoint()) && Objects.equals(credentialRef, connector.getCredentialRef())
                && Objects.equals(configJson, connector.getConfigJson());
    }

    public Credentials credentials(Function<String, String> environment, ObjectMapper mapper) {
        try {
            JsonNode secret = mapper.readTree(environment.apply(credentialRef));
            if (!secret.path("username").isTextual() || secret.path("username").asText().isBlank()
                    || !secret.path("password").isTextual() || secret.path("password").asText().isEmpty()) {
                throw new IllegalArgumentException();
            }
            return new Credentials(secret.path("username").asText(), secret.path("password").asText().toCharArray());
        } catch (Exception ex) {
            throw new IllegalArgumentException("MQTT 凭据引用不存在或内容无效");
        }
    }

    /** 不使用自动生成的字符串输出，防止凭据被意外写入日志。 */
    public record Credentials(String username, char[] password) {
        @Override
        public String toString() { return "MQTT 凭据[已隐藏]"; }
    }
}
