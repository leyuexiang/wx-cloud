package cn.iocoder.wx.module.dimensions.framework.rpc.config;

import cn.iocoder.wx.module.infra.api.websocket.WebSocketSenderApi;

import org.springframework.cloud.openfeign.EnableFeignClients;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import cn.iocoder.wx.module.system.api.tenant.TenantApi;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
// 显式登记目录操作人查询和场景授权目标租户校验所用的远程客户端。
@EnableFeignClients(clients = {WebSocketSenderApi.class, DictDataApi.class, AdminUserApi.class, TenantApi.class})
public class RpcConfiguration {
}
