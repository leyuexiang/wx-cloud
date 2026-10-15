package cn.iocoder.wx.module.vegetation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** 注册Python生成器客户端依赖并限制网络请求超时，避免接口线程无限等待。 */
@Configuration
@EnableConfigurationProperties(VegetationGeneratorProperties.class)
public class VegetationGeneratorConfig {

    @Bean
    public RestTemplate vegetationRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
