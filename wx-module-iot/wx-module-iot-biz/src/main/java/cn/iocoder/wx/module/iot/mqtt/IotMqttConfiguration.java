package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.service.IotConnectorService;
import cn.iocoder.wx.module.iot.service.IotTelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** MQTT 上行独立开关，关闭时不创建租约、网络客户端或后台工作线程。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "wx.iot.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IotMqttProperties.class)
public class IotMqttConfiguration {
    @Bean
    public IotMqttTelemetryAdapter iotMqttTelemetryAdapter(IotConnectorService connectors,
            TenantFrameworkService tenants, IotTelemetryService telemetry, Validator validator, ObjectMapper mapper) {
        return new IotMqttTelemetryAdapter(connectors, tenants, telemetry, validator, mapper);
    }

    @Bean
    public IotMqttLease iotMqttLease(StringRedisTemplate redis, IotMqttProperties properties) {
        return new IotMqttLease(redis, properties.getTenantId(), properties.getConnectorId());
    }

    @Bean
    public IotMqttConnection.Factory iotMqttConnectionFactory(IotMqttProperties properties,
            IotMqttTelemetryAdapter adapter, ObjectMapper mapper) {
        return (settings, lease) -> {
            var credentials = settings.credentials(System::getenv, mapper);
            try {
                return new PahoIotMqttConnection(new MqttAsyncClient(settings.endpoint(), settings.clientId(),
                        new MemoryPersistence()), settings, credentials, lease,
                        (topic, payload) -> adapter.ingest(properties.getTenantId(), properties.getConnectorId(),
                                settings, topic, payload));
            } catch (MqttException ex) {
                java.util.Arrays.fill(credentials.password(), '\0');
                throw new IllegalStateException("MQTT 客户端无法创建");
            }
        };
    }

    @Bean
    public IotMqttWorker iotMqttWorker(IotMqttProperties properties, IotConnectorMapper connectors,
            TenantFrameworkService tenants, IotMqttLease lease, IotMqttConnection.Factory factory, ObjectMapper mapper) {
        return new IotMqttWorker(properties, connectors, tenants, lease, factory, mapper);
    }
}
