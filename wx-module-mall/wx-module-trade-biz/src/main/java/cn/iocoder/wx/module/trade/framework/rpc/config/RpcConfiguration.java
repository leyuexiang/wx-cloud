package cn.iocoder.wx.module.trade.framework.rpc.config;

import cn.iocoder.wx.module.member.api.address.MemberAddressApi;
import cn.iocoder.wx.module.member.api.config.MemberConfigApi;
import cn.iocoder.wx.module.member.api.level.MemberLevelApi;
import cn.iocoder.wx.module.member.api.point.MemberPointApi;
import cn.iocoder.wx.module.member.api.user.MemberUserApi;
import cn.iocoder.wx.module.pay.api.order.PayOrderApi;
import cn.iocoder.wx.module.pay.api.refund.PayRefundApi;
import cn.iocoder.wx.module.product.api.category.ProductCategoryApi;
import cn.iocoder.wx.module.product.api.comment.ProductCommentApi;
import cn.iocoder.wx.module.product.api.sku.ProductSkuApi;
import cn.iocoder.wx.module.product.api.spu.ProductSpuApi;
import cn.iocoder.wx.module.promotion.api.bargain.BargainActivityApi;
import cn.iocoder.wx.module.promotion.api.bargain.BargainRecordApi;
import cn.iocoder.wx.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.wx.module.promotion.api.coupon.CouponApi;
import cn.iocoder.wx.module.promotion.api.discount.DiscountActivityApi;
import cn.iocoder.wx.module.promotion.api.point.PointActivityApi;
import cn.iocoder.wx.module.promotion.api.reward.RewardActivityApi;
import cn.iocoder.wx.module.promotion.api.seckill.SeckillActivityApi;
import cn.iocoder.wx.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.wx.module.system.api.social.SocialClientApi;
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
