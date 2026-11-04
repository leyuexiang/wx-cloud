package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.consumer.IotStateEventConsumer;
import cn.iocoder.wx.module.dimensions.controller.admin.IotStateController;
import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageDeserializer;
import cn.iocoder.wx.framework.websocket.core.sender.kafka.KafkaWebSocketMessageSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 防止专用 Kafka 资源使旧链路自动配置退让，并验证默认关闭及保留期约束。 */
class IotStateConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(IotStateProjectionConfiguration.class, IotStateEventConsumer.class, IotStateController.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(SecurityFrameworkService.class, () -> mock(SecurityFrameworkService.class))
            .withPropertyValues("spring.kafka.listener.auto-startup=false",
                    "spring.kafka.producer.value-serializer=" + KafkaWebSocketMessageSerializer.class.getName(),
                    "spring.kafka.consumer.value-deserializer=" + KafkaWebSocketMessageDeserializer.class.getName());

    @Test
    void shouldBeDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(IotStateProjectionService.class);
            assertThat(context).doesNotHaveBean("iotStateKafkaListenerContainerFactory");
            assertThat(context).doesNotHaveBean(IotStateController.class).doesNotHaveBean(IotStateEventConsumer.class);
        });
    }

    @Test
    void shouldKeepLegacyKafkaBeansAndUseDedicatedStringConsumer() {
        runner.withPropertyValues("wx.iot-state.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(KafkaTemplate.class)
                    .hasBean("kafkaListenerContainerFactory").hasSingleBean(IotStateProjectionService.class);
            assertThat(context).hasSingleBean(IotStateController.class).hasSingleBean(IotStateEventConsumer.class);
            assertThat(context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers()).hasSize(1);
            KafkaTemplate<?, ?> legacyTemplate = context.getBean(KafkaTemplate.class);
            assertThat(legacyTemplate.getProducerFactory().getConfigurationProperties().get("value.serializer"))
                    .isEqualTo(KafkaWebSocketMessageSerializer.class);
            var factory = context.getBean("iotStateKafkaListenerContainerFactory",
                    ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.RECORD);
            var config = factory.getConsumerFactory().getConfigurationProperties();
            assertThat(config.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
            assertThat(config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        });
    }

    @Test
    void shouldRejectZeroRetention() {
        runner.withPropertyValues("wx.iot-state.enabled=true", "wx.iot-state.state-ttl=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectDeadLetterLoop() {
        runner.withPropertyValues("wx.iot-state.enabled=true", "wx.iot-state.dead-letter-topic=twin.event.v1")
                .run(context -> assertThat(context).hasFailed());
    }
}
