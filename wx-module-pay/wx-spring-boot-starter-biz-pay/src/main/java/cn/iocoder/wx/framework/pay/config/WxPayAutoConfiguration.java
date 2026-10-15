package cn.iocoder.wx.framework.pay.config;

import cn.iocoder.wx.framework.pay.core.client.PayClientFactory;
import cn.iocoder.wx.framework.pay.core.client.impl.PayClientFactoryImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 支付配置类
 *
 * @author 九天芸菲
 */
@AutoConfiguration
public class WxPayAutoConfiguration {

    @Bean
    public PayClientFactory payClientFactory() {
        return new PayClientFactoryImpl();
    }

}
