package cn.iocoder.wx.module.system.framework.rpc.config;

import cn.iocoder.wx.module.infra.api.config.ConfigApi;
import cn.iocoder.wx.module.infra.api.file.FileApi;
import cn.iocoder.wx.module.infra.api.websocket.WebSocketSenderApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {FileApi.class, WebSocketSenderApi.class, ConfigApi.class})
public class RpcConfiguration {
}
