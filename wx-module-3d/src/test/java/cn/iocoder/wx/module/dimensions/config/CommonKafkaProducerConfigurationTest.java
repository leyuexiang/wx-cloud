package cn.iocoder.wx.module.dimensions.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证批量接口继承环境配置，同时保留旧 WebSocket 生产者自动配置。 */
class CommonKafkaProducerConfigurationTest {
    @Test
    void shouldInheritBrokerAndSecurityWithoutReplacingLegacyTemplate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withUserConfiguration(CommonKafkaProducerConfiguration.class)
                .withPropertyValues("spring.kafka.bootstrap-servers=broker.example:19092",
                        "spring.kafka.properties.security.protocol=SASL_SSL",
                        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.ByteArraySerializer")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(KafkaTemplate.class);
                    var resources = context.getBean(CommonKafkaProducerConfiguration.StringProducerResources.class);
                    var config = resources.template().getProducerFactory().getConfigurationProperties();
                    assertThat(config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).asList().containsExactly("broker.example:19092");
                    assertThat(config.get("security.protocol")).isEqualTo("SASL_SSL");
                    assertThat(config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
                    assertThat(context.getBean(KafkaTemplate.class)).isNotSameAs(resources.template());
                });
    }
}
