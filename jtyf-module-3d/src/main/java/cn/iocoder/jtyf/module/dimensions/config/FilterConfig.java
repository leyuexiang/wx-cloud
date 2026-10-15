package cn.iocoder.jtyf.module.dimensions.config;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

@Configuration
//@EnableConfigurationProperties(AppProperties.class)
public class FilterConfig {

    /**
     * 跨域过滤器
     */
    @Bean
    @ConditionalOnProperty(name = "unionbigdata.Web.cors-filter",havingValue = "true",matchIfMissing = false)
    public CorsWebFilter corsFilter(@Value("${sa-token.token-name:satoken}") String tokenName){
        CorsConfiguration config = new CorsConfiguration();
        UrlBasedCorsConfigurationSource source
                = new UrlBasedCorsConfigurationSource(new PathPatternParser());
        // 允许cookies跨域
        config.setAllowCredentials(true);
        // #允许向该服务器提交请求的URI，*表示全部允许，在SpringMVC中，如果设成*，会自动转成当前请求头中的Origin
        config.addAllowedOriginPattern("*");
        // #允许访问的头信息,*表示全部
        config.addAllowedHeader("*");
        // 暴露header sa-token.token-name:satoken
        config.addExposedHeader(tokenName);
        // 预检请求的缓存时间（秒），即在这个时间段里，对于相同的跨域请求不会再预检了
        config.setMaxAge(18000L);
        // 允许提交请求的方法类型，*表示全部允许
        config.addAllowedMethod("*");
        //可访问的外部域
        config.addAllowedOrigin("*");

        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }




}

