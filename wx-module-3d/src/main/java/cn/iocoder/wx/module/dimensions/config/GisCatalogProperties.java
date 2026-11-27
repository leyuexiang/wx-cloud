package cn.iocoder.wx.module.dimensions.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** 仅由部署配置登记已核验的共享来源，客户端不能指定存储地址。 */
@Data
@Component
@ConfigurationProperties(prefix = "dim3d.gis.catalog")
public class GisCatalogProperties {
    private boolean enabled;
    private long cacheSeconds = 60;
    private int connectTimeoutMillis = 2000;
    private int readTimeoutMillis = 4000;
    private List<Source> sources = new ArrayList<>();

    @Data
    public static class Source {
        private String key;
        private String name;
        private String dataType;
        private String metadataUrl;
        private String publicUrl;
        private Integer maximumLevel;
    }
}
