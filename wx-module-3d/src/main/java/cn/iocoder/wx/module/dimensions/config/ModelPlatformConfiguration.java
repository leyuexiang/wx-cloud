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

/** 模型平台入口在进入目录、上传或导入逻辑之前统一校验登录身份。 */
@Configuration(proxyBeanMethods = false)
public class ModelPlatformConfiguration {
    public static final String PLATFORM_SEGMENT = "/{modelPlatform:operation|tenant}";

    @Bean
    public MappedInterceptor modelPlatformAccess(WebProperties web, ArtResourceAccess access) {
        String modelRoutes = web.getAdminApi().getPrefix() + "/dim3d" + PLATFORM_SEGMENT + "/**";
        return new MappedInterceptor(new String[]{modelRoutes}, new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                // 平台标识取自框架已匹配的路径，认证身份仍由访问服务从令牌读取。
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Object platform = variables instanceof Map<?, ?> values ? values.get("modelPlatform") : null;
                access.requireModelPlatform((String) platform);
                return true;
            }
        });
    }
}
