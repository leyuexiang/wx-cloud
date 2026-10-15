package cn.iocoder.jtyf.module.dimensions.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ author 9tyf
 * @ desc Minio配置
 * @ date 2024-11-5
 */
@Configuration
public class MinioConfig {

    @Value("${minio.url}")
    private String url;
    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;

    @Bean(name = "3dMinioClient")
    public MinioClient getMinioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

}
