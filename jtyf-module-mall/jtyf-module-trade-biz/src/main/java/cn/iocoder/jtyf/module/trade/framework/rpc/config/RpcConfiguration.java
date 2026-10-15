package cn.iocoder.jtyf.module.trade.framework.rpc.config;

import cn.iocoder.jtyf.module.member.api.address.MemberAddressApi;
import cn.iocoder.jtyf.module.member.api.config.MemberConfigApi;
import cn.iocoder.jtyf.module.member.api.level.MemberLevelApi;
import cn.iocoder.jtyf.module.member.api.point.MemberPointApi;
import cn.iocoder.jtyf.module.member.api.user.MemberUserApi;
import cn.iocoder.jtyf.module.pay.api.order.PayOrderApi;
import cn.iocoder.jtyf.module.pay.api.refund.PayRefundApi;
import cn.iocoder.jtyf.module.product.api.category.ProductCategoryApi;
import cn.iocoder.jtyf.module.product.api.comment.ProductCommentApi;
import cn.iocoder.jtyf.module.product.api.sku.ProductSkuApi;
import cn.iocoder.jtyf.module.product.api.spu.ProductSpuApi;
import cn.iocoder.jtyf.module.promotion.api.bargain.BargainActivityApi;
import cn.iocoder.jtyf.module.promotion.api.bargain.BargainRecordApi;
import cn.iocoder.jtyf.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.jtyf.module.promotion.api.coupon.CouponApi;
import cn.iocoder.jtyf.module.promotion.api.discount.DiscountActivityApi;
import cn.iocoder.jtyf.module.promotion.api.point.PointActivityApi;
import cn.iocoder.jtyf.module.promotion.api.reward.RewardActivityApi;
import cn.iocoder.jtyf.module.promotion.api.seckill.SeckillActivityApi;
import cn.iocoder.jtyf.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.jtyf.module.system.api.social.SocialClientApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {
        BargainActivityApi.class, BargainRecordApi.class, CombinationRecordApi.class,
        CouponApi.class, DiscountActivityApi.class, RewardActivityApi.class, SeckillActivityApi.class, PointActivityApi.class,
        MemberUserApi.class, MemberPointApi.class, MemberLevelApi.class, MemberAddressApi.class, MemberConfigApi.class,
        ProductSpuApi.class, ProductSkuApi.class, ProductCommentApi.class, ProductCategoryApi.class,
        PayOrderApi.class, PayRefundApi.class,
        NotifyMessageSendApi.class, SocialClientApi.class
})
public class RpcConfiguration {
}
