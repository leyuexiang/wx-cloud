package cn.iocoder.wx.module.erp.service.statistics;

import cn.iocoder.wx.module.erp.dal.mysql.statistics.ErpPurchaseStatisticsMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ERP 采购统计 Service 实现类
 *
 * @author 九天芸菲
 */
@Service
public class ErpPurchaseStatisticsServiceImpl implements ErpPurchaseStatisticsService {

    @Resource
    private ErpPurchaseStatisticsMapper purchaseStatisticsMapper;

    @Override
    public BigDecimal getPurchasePrice(LocalDateTime beginTime, LocalDateTime endTime) {
        return purchaseStatisticsMapper.getPurchasePrice(beginTime, endTime);
    }

}
