package cn.iocoder.wx.framework.autoconfigure;

import cn.iocoder.wx.framework.core.LicenseCheckApplicationRunner;
import cn.iocoder.wx.framework.core.aop.LicenseAspect;
import cn.iocoder.wx.framework.core.prop.LicenseProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author xwd
 * @version 1.0
 * @date 2025-04-03
 */
@Configuration
@ComponentScan(basePackages = {"cn.iocoder.wx.framework"})
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseAutoConfiguration {


    @Bean
    @ConditionalOnProperty(name = "ptc.license.start-check", havingValue = "true", matchIfMissing = true)
    public LicenseCheckApplicationRunner licenseCheckApplicationRunner() {
        return new LicenseCheckApplicationRunner();
    }

    @Bean
    public LicenseAspect licenseAspect() {
        return new LicenseAspect();
    }


}
