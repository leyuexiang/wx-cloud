package cn.iocoder.wx.module.dimensions.framework.rpc.config;

import cn.iocoder.wx.module.infra.api.websocket.WebSocketSenderApi;

import org.springframework.cloud.openfeign.EnableFeignClients;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
// 模型响应的操作人名称复用系统批量用户查询，显式注册对应远程客户端。
@EnableFeignClients(clients = {WebSocketSenderApi.class, DictDataApi.class, AdminUserApi.class})
public class RpcConfiguration {
}
