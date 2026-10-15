package cn.iocoder.jtyf.module.trade.job.order;

import cn.iocoder.jtyf.framework.tenant.core.job.TenantJob;
import cn.iocoder.jtyf.module.trade.service.order.TradeOrderUpdateService;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 交易订单的自动过期 Job
 *
 * @author 九天芸菲
 */
@Component
public class TradeOrderAutoCancelJob {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;

    @XxlJob("tradeOrderAutoCancelJob")
    @TenantJob // 多租户
    public String execute() {
        int count = tradeOrderUpdateService.cancelOrderBySystem();
        return String.format("过期订单 %s 个", count);
    }

}
