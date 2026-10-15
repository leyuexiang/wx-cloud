package cn.iocoder.jtyf.framework.pay.config;

import cn.iocoder.jtyf.framework.pay.core.client.PayClientFactory;
import cn.iocoder.jtyf.framework.pay.core.client.impl.PayClientFactoryImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 支付配置类
 *
 * @author 九天芸菲
 */
@AutoConfiguration
public class JtyfPayAutoConfiguration {

    @Bean
    public PayClientFactory payClientFactory() {
        return new PayClientFactoryImpl();
    }

}
