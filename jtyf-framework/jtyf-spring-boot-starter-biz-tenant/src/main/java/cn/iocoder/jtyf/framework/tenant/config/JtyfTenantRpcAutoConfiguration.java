package cn.iocoder.jtyf.framework.tenant.config;

import cn.iocoder.jtyf.framework.tenant.core.rpc.TenantRequestInterceptor;
import cn.iocoder.jtyf.module.system.api.tenant.TenantApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "jtyf.tenant", value = "enable", matchIfMissing = true) // 允许使用 jtyf.tenant.enable=false 禁用多租户
@EnableFeignClients(clients = TenantApi.class) // 主要是引入相关的 API 服务
public class JtyfTenantRpcAutoConfiguration {

    @Bean
    public TenantRequestInterceptor tenantRequestInterceptor() {
        return new TenantRequestInterceptor();
    }

}
