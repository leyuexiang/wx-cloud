package cn.iocoder.wx.module.infra.framework.file.config;

import cn.iocoder.wx.module.infra.framework.file.core.client.FileClientFactory;
import cn.iocoder.wx.module.infra.framework.file.core.client.FileClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件配置类
 *
 * @author 九天芸菲
 */
@Configuration(proxyBeanMethods = false)
public class WxFileAutoConfiguration {

    @Bean
    public FileClientFactory fileClientFactory() {
        return new FileClientFactoryImpl();
    }

}
