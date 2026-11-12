package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.security.config.AuthorizeRequestsCustomizer;
import cn.iocoder.wx.framework.web.config.WebProperties;
import cn.iocoder.wx.module.iot.config.IotSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** 复现独立服务缺少安全定制列表项的启动条件，同时防止修复误放开管理接口。 */
class IotSecurityConfigurationTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldProvideRequiredCustomizerAndOnlyAllowExactHealthProbe() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(WebProperties.class, WebProperties::new);
            context.register(IotSecurityConfiguration.class);
            context.refresh();
            var beans = context.getBeansOfType(AuthorizeRequestsCustomizer.class);
            assertEquals(1, beans.size());
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry =
                    mock(AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class, RETURNS_DEEP_STUBS);
            var health = registry.requestMatchers(HttpMethod.GET, "/actuator/health");
            clearInvocations(registry);
            beans.values().iterator().next().customize(registry);
            verify(registry).requestMatchers(HttpMethod.GET, "/actuator/health");
            verifyNoMoreInteractions(registry);
            verify(health).permitAll();
        }
    }
}
