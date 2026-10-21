package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** 专用编解码和错误处理，避免影响三维服务旧 WebSocket Kafka 消费者。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IotStateProjectionProperties.class)
@ConditionalOnProperty(prefix = "wx.iot-state", name = "enabled", havingValue = "true")
public class IotStateProjectionConfiguration {

    @Bean
    public IotTelemetryEventCodec iotTelemetryEventCodec() {
        return new IotTelemetryEventCodec();
    }

    @Bean
    public IotStateStore iotStateStore(StringRedisTemplate redis, IotTelemetryEventCodec codec) {
        return new RedisIotStateStore(redis, codec);
    }

    @Bean
    public IotStateProjectionService iotStateProjectionService(IotStateStore store,
            IotTelemetryEventCodec codec, IotStateProjectionProperties properties) {
        return new IotStateProjectionService(store, codec, properties, Clock.systemUTC());
    }

    @Bean
    public IotStateQueryService iotStateQueryService(IotStateStore store, SecurityFrameworkService security) {
        return new IotStateQueryService(store, security);
    }

    @Bean(destroyMethod = "close")
    public DeadLetterResources iotStateDeadLetterResources(KafkaProperties kafkaProperties) {
        return new DeadLetterResources(kafkaProperties.buildProducerProperties());
    }

    @Bean("iotStateKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> iotStateKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties, IotStateProjectionProperties properties, DeadLetterResources resources) {
        Map<String, Object> consumer = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumer.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumer));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setMissingTopicsFatal(true);
        factory.setAutoStartup(kafkaProperties.getListener().isAutoStartup());
        factory.setCommonErrorHandler(createErrorHandler(resources.template, properties.getDeadLetterTopic()));
        return factory;
    }

    /** 死信确认失败必须返回消费失败；不能在原文尚未可靠隔离时推进消费位点。 */
    static DefaultErrorHandler createErrorHandler(KafkaOperations<String, String> template, String deadLetterTopic) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, error) -> new TopicPartition(deadLetterTopic, -1));
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler errors = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 2));
        errors.addNotRetryableExceptions(IotTelemetryEventCodec.InvalidEventException.class);
        return errors;
    }

    /** 不以 KafkaTemplate/ProducerFactory 类型注册 Bean，避免使旧链路的 Boot 自动配置退让。 */
    public static class DeadLetterResources implements AutoCloseable {
        private final DefaultKafkaProducerFactory<String, String> producerFactory;
        private final KafkaTemplate<String, String> template;

        DeadLetterResources(Map<String, Object> existingProperties) {
            Map<String, Object> producer = new HashMap<>(existingProperties);
            producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer.put(ProducerConfig.ACKS_CONFIG, "all");
            producer.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            producerFactory = new DefaultKafkaProducerFactory<>(producer);
            template = new KafkaTemplate<>(producerFactory);
        }

        @Override
        public void close() {
            template.destroy();
            producerFactory.destroy();
        }
    }
}
