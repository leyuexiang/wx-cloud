package cn.iocoder.jtyf.module.ai.framework.rpc.config;

import cn.iocoder.jtyf.module.infra.api.file.FileApi;
import cn.iocoder.jtyf.module.system.api.dict.DictDataApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {DictDataApi.class, FileApi.class})
public class RpcConfiguration {
}
