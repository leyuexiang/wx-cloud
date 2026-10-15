package cn.iocoder.jtyf.framework.signature.config;

import cn.iocoder.jtyf.framework.redis.config.JtyfRedisAutoConfiguration;
import cn.iocoder.jtyf.framework.signature.core.aop.ApiSignatureAspect;
import cn.iocoder.jtyf.framework.signature.core.redis.ApiSignatureRedisDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * HTTP API 签名的自动配置类
 *
 * @author Zhougang
 */
@AutoConfiguration(after = JtyfRedisAutoConfiguration.class)
public class JtyfApiSignatureAutoConfiguration {

    @Bean
    public ApiSignatureAspect signatureAspect(ApiSignatureRedisDAO signatureRedisDAO) {
        return new ApiSignatureAspect(signatureRedisDAO);
    }

    @Bean
    public ApiSignatureRedisDAO signatureRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new ApiSignatureRedisDAO(stringRedisTemplate);
    }

}
