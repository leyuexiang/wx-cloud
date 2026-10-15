package cn.iocoder.jtyf.module.infra.framework.rpc.config;

import cn.iocoder.jtyf.module.system.api.user.AdminUserApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = AdminUserApi.class)
public class RpcConfiguration {
}
