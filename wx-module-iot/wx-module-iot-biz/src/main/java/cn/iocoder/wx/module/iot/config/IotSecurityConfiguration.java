package cn.iocoder.wx.module.iot.config;

import cn.iocoder.wx.framework.security.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/** 独立物联网服务复用安全框架，管理接口继续要求可信登录及方法权限。 */
@Configuration(proxyBeanMethods = false)
public class IotSecurityConfiguration {

    /** 框架必需模块定制列表；仅允许无详情的健康探测，管理接口继续要求登录。 */
    @Bean("iotAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {
            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>
                    .AuthorizationManagerRequestMatcherRegistry registry) {
                registry.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                // 管理端仍使用框架默认登录校验，细分权限由控制器PreAuthorize逐次校验。
            }
        };
    }
}
