package cn.iocoder.wx.module.dimensions.config;

import cn.iocoder.wx.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.wx.module.system.enums.ApiConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 3d 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "dimensionSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("dimensionAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
                // TODO 芸菲：这个每个项目都需要重复配置，得捉摸有没通用的方案
                // Swagger 接口文档
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll();
                // Druid 监控
                registry.requestMatchers("/druid/**").permitAll();
                // Spring Boot Actuator 的安全配置
                registry.requestMatchers("/actuator").permitAll()
                        .requestMatchers("/actuator/**").permitAll();
                // RPC 服务的安全配置
                registry.requestMatchers(ApiConstants.PREFIX + "/**").permitAll();
                registry.requestMatchers("/**").permitAll();
            }

        };
    }

    /**
     * 方法权限校验配置，自定义表达式处理器，可通过此处控制方法权限校验是否开启
     */
    //@Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new CustomMethodSecurityExpressionHandler();
    }

    /**
     * 过滤指定包的权限校验
     * 通过覆盖 PreAuthorizeAuthorizationManager
     * PreAuthorizeExpressionAttributeRegistry 获取的表达式处理器，实现自定义表达式处理器
     */
    private class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
        // 通过初始化块替换表达式解析器
        {
            setExpressionParser(new CustomExpressionParser());
        }

        private class CustomExpressionParser extends SpelExpressionParser {
            @Override
            public Expression parseExpression(String expressionStr) throws ParseException {
                // 目前权限校验全部放行
                return super.parseExpression("true");
            }
        }
    }
}
