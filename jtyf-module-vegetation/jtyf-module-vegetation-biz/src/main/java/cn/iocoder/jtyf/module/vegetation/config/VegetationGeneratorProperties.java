package cn.iocoder.jtyf.module.vegetation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Python生成器连接配置；地址通过配置中心覆盖，代码不写死环境地址。 */
@ConfigurationProperties(prefix = "jtyf.vegetation.generator")
public class VegetationGeneratorProperties {

    private String baseUrl = "http://127.0.0.1:48095";
    /** 与Python按档位聚合镜像版本0.3.1一致的服务端冻结值，禁止由浏览器任意指定。 */
    private String version = "0.3.1";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
