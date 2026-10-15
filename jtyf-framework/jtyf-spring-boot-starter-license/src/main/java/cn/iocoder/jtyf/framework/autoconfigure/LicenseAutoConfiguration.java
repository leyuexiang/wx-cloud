package cn.iocoder.jtyf.framework.autoconfigure;

import cn.iocoder.jtyf.framework.core.LicenseCheckApplicationRunner;
import cn.iocoder.jtyf.framework.core.aop.LicenseAspect;
import cn.iocoder.jtyf.framework.core.prop.LicenseProperties;
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
@ComponentScan(basePackages = {"cn.iocoder.jtyf.framework"})
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
