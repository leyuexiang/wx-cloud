package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import cn.iocoder.wx.module.iot.service.IotConnectorService;
import cn.iocoder.wx.module.iot.service.IotTelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 配置装配只使用模拟 Redis，验证默认关闭与必要部署身份，不访问外部依赖。 */
class IotMqttConfigurationTest {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(IotMqttConfiguration.class)
            .withBean(IotConnectorMapper.class, () -> mock(IotConnectorMapper.class))
            .withBean(IotConnectorService.class, () -> mock(IotConnectorService.class))
            .withBean(IotTelemetryService.class, () -> mock(IotTelemetryService.class))
            .withBean(TenantFrameworkService.class, () -> mock(TenantFrameworkService.class))
            .withBean(ObjectMapper.class, () -> new ObjectMapper().registerModule(new JavaTimeModule()))
            .withBean(Validator.class, VALIDATORS::getValidator)
            .withBean(StringRedisTemplate.class, () -> {
                StringRedisTemplate redis = mock(StringRedisTemplate.class);
                @SuppressWarnings("unchecked")
                ValueOperations<String, String> values = mock(ValueOperations.class);
                when(redis.opsForValue()).thenReturn(values);
                return redis;
            });

    @AfterAll
    static void closeValidators() { VALIDATORS.close(); }

    @Test
    void shouldCreateNoMqttResourcesByDefault() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(IotMqttWorker.class)
                .doesNotHaveBean(IotMqttLease.class));
    }

    @Test
    void shouldRejectEnabledWorkerWithoutExplicitTenantAndConnector() {
        runner.withPropertyValues("wx.iot.mqtt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldAssembleWorkerWithoutReplacingExistingTelemetryService() {
        runner.withPropertyValues("wx.iot.mqtt.enabled=true", "wx.iot.mqtt.tenant-id=7",
                "wx.iot.mqtt.connector-id=pilot")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(IotMqttWorker.class)
                        .hasSingleBean(IotMqttConnection.Factory.class).hasSingleBean(IotTelemetryService.class));
    }
}
