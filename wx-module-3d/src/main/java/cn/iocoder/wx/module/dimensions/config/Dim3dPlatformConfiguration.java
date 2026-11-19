package cn.iocoder.wx.module.dimensions.config;

import cn.iocoder.wx.framework.web.config.WebProperties;
import cn.iocoder.wx.module.dimensions.service.ArtResourceAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.Map;

/** 模型、图标和场景的双端入口在执行控制器方法前核对登录端。 */
@Configuration(proxyBeanMethods = false)
public class Dim3dPlatformConfiguration {
    public static final String PLATFORM_SEGMENT = "/{dim3dPlatform:operation|tenant}";

    @Bean
    public MappedInterceptor dim3dPlatformAccess(WebProperties web, ArtResourceAccess access) {
        String platformRoutes = web.getAdminApi().getPrefix() + "/dim3d" + PLATFORM_SEGMENT + "/**";
        return new MappedInterceptor(new String[]{platformRoutes}, new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 平台标识取自框架已匹配的路径，认证身份仍由访问服务从令牌读取。
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Object platform = variables instanceof Map<?, ?> values ? values.get("dim3dPlatform") : null;
                access.requireDim3dPlatform(platform instanceof String name ? name : null);
                return true;
            }
        });
    }
}
