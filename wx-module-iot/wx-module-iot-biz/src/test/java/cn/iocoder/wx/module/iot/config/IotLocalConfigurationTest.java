package cn.iocoder.wx.module.iot.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 检查开发配置实际解析结果，确保部署参数注入及必填凭据约束生效。 */
class IotLocalConfigurationTest {

    /** 数据库连接信息必须可由部署环境提供，不能依赖开发者本机固定凭据。 */
    @Test
    void shouldResolveDatabaseAndInfrastructureOverrides() throws Exception {
        StandardEnvironment environment = environment(Map.of(
                "IOT_DB_URL", "jdbc:mysql://db:3306/iot_test",
                "IOT_DB_USERNAME", "iot_test", "IOT_DB_PASSWORD", "test-only-password",
                "IOT_REDIS_HOST", "redis-test", "IOT_KAFKA_BOOTSTRAP_SERVERS", "broker-test:9092",
                "IOT_NACOS_SERVER_ADDR", "nacos-test:8848", "IOT_NACOS_PASSWORD", "test-only-nacos"));
        assertEquals("jdbc:mysql://db:3306/iot_test", environment.getProperty("spring.datasource.dynamic.datasource.master.url"));
        assertEquals("iot_test", environment.getProperty("spring.datasource.dynamic.datasource.master.username"));
        assertEquals("test-only-password", environment.getProperty("spring.datasource.dynamic.datasource.master.password"));
        assertEquals("redis-test", environment.getProperty("spring.data.redis.host"));
        assertEquals("broker-test:9092", environment.getProperty("spring.kafka.bootstrap-servers"));
        assertEquals("nacos-test:8848", environment.getProperty("spring.cloud.nacos.server-addr"));
        assertEquals("test-only-nacos", environment.getProperty("spring.cloud.nacos.password"));
    }

    /** 缺少数据库密码必须报错，不应悄悄使用仓库内默认密码。 */
    @Test
    void shouldRequireDatabasePassword() throws Exception {
        StandardEnvironment environment = environment(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> environment.getProperty("spring.datasource.dynamic.datasource.master.password"));
    }

    /** 注册中心认证已启用，开发配置不能提供可复用的默认口令。 */
    @Test
    void shouldRequireNacosPassword() throws Exception {
        StandardEnvironment environment = environment(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> environment.getProperty("spring.cloud.nacos.password"));
    }

    /** 开发环境同样启用全部副本确认与生产者幂等，避免重试制造可避免的重复投递。 */
    @Test
    void shouldUseAcknowledgedIdempotentProducer() throws Exception {
        StandardEnvironment environment = environment(Map.of());
        assertEquals("all", environment.getProperty("spring.kafka.producer.acks"));
        assertEquals("true", environment.getProperty("spring.kafka.producer.properties.enable.idempotence"));
        assertEquals("true", environment.getProperty("spring.datasource.dynamic.strict"));
    }

    /** 仅加载待验证配置，移除宿主机环境变量，确保测试不受真实凭据或其他任务影响。 */
    private StandardEnvironment environment(Map<String, Object> overrides) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        new YamlPropertySourceLoader().load("iot-local", new ClassPathResource("application-local.yaml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        environment.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
        return environment;
    }
}
