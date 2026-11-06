package cn.iocoder.wx.module.dimensions.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

/** 批量接口复用部署的 Kafka 地址和安全配置，生产者由容器统一复用及释放。 */
@Configuration(proxyBeanMethods = false)
public class CommonKafkaProducerConfiguration {
    @Bean(destroyMethod = "close")
    public StringProducerResources commonStringProducerResources(KafkaProperties properties) {
        return new StringProducerResources(properties.buildProducerProperties());
    }

    /** 包装专用字符串生产者，避免注册 KafkaTemplate 导致旧 WebSocket 自动配置退让。 */
    public static class StringProducerResources implements AutoCloseable {
        private final DefaultKafkaProducerFactory<String, String> factory;
        private final KafkaTemplate<String, String> template;

        StringProducerResources(Map<String, Object> properties) {
            Map<String, Object> config = new HashMap<>(properties);
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            factory = new DefaultKafkaProducerFactory<>(config);
            template = new KafkaTemplate<>(factory);
        }

        public KafkaTemplate<String, String> template() { return template; }

        @Override
        public void close() {
            template.destroy();
            factory.destroy();
        }
    }
}
