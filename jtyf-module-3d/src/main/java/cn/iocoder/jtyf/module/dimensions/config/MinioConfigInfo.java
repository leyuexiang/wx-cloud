package cn.iocoder.jtyf.module.dimensions.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioConfigInfo {

    private String url;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private Integer expiry;
    private Integer breakpointTime;
}
