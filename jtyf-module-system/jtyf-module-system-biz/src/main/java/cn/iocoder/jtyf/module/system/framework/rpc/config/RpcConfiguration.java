package cn.iocoder.jtyf.module.system.framework.rpc.config;

import cn.iocoder.jtyf.module.infra.api.config.ConfigApi;
import cn.iocoder.jtyf.module.infra.api.file.FileApi;
import cn.iocoder.jtyf.module.infra.api.websocket.WebSocketSenderApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {FileApi.class, WebSocketSenderApi.class, ConfigApi.class})
public class RpcConfiguration {
}
