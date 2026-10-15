package cn.iocoder.jtyf.module.promotion.framework.rpc.config;

import cn.iocoder.jtyf.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.jtyf.module.member.api.user.MemberUserApi;
import cn.iocoder.jtyf.module.product.api.category.ProductCategoryApi;
import cn.iocoder.jtyf.module.product.api.sku.ProductSkuApi;
import cn.iocoder.jtyf.module.product.api.spu.ProductSpuApi;
import cn.iocoder.jtyf.module.system.api.social.SocialClientApi;
import cn.iocoder.jtyf.module.system.api.user.AdminUserApi;
import cn.iocoder.jtyf.module.trade.api.order.TradeOrderApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {ProductSkuApi.class, ProductSpuApi.class, ProductCategoryApi.class,
        MemberUserApi.class, TradeOrderApi.class, AdminUserApi.class, SocialClientApi.class,
        WebSocketSenderApi.class})
public class RpcConfiguration {
}
