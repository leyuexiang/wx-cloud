package cn.iocoder.wx.module.promotion.framework.rpc.config;

import cn.iocoder.wx.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.wx.module.member.api.user.MemberUserApi;
import cn.iocoder.wx.module.product.api.category.ProductCategoryApi;
import cn.iocoder.wx.module.product.api.sku.ProductSkuApi;
import cn.iocoder.wx.module.product.api.spu.ProductSpuApi;
import cn.iocoder.wx.module.system.api.social.SocialClientApi;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import cn.iocoder.wx.module.trade.api.order.TradeOrderApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {ProductSkuApi.class, ProductSpuApi.class, ProductCategoryApi.class,
        MemberUserApi.class, TradeOrderApi.class, AdminUserApi.class, SocialClientApi.class,
        WebSocketSenderApi.class})
public class RpcConfiguration {
}
