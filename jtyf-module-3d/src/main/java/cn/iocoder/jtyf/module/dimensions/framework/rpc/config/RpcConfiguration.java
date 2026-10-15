package cn.iocoder.jtyf.module.dimensions.framework.rpc.config;

import cn.iocoder.jtyf.module.infra.api.websocket.WebSocketSenderApi;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = WebSocketSenderApi.class)
public class RpcConfiguration {
}
