package cn.iocoder.wx.module.semi.controller.admin.directory.conf;

import cn.iocoder.wx.module.semi.common.presentation.interceptor.ApiAuthChkInterceptor;
import cn.iocoder.wx.module.semi.common.presentation.interceptor.ApiCorsInterceptor;
import cn.iocoder.wx.module.semi.common.util.SpringUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class DirWebMvcConfig implements WebMvcConfigurer {

    private static final String PATH_PREFIX = "/admin-api/api/directory";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(SpringUtil.getBean(ApiCorsInterceptor.class, "apiCorsInterceptor"))
                .addPathPatterns(PATH_PREFIX + "/**");

        registry.addInterceptor(SpringUtil.getBean(ApiAuthChkInterceptor.class, "apiAuthChkInterceptor"))
                .addPathPatterns(PATH_PREFIX + "/**");
    }
}
