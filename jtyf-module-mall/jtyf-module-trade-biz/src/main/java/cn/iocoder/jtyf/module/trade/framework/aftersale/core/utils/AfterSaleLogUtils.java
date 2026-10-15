package cn.iocoder.jtyf.module.trade.framework.aftersale.core.utils;


import cn.iocoder.jtyf.module.trade.framework.aftersale.core.aop.AfterSaleLogAspect;

import java.util.Map;

/**
 * 操作日志工具类
 * 目前主要的作用，是提供给业务代码，记录操作明细和拓展字段
 *
 * @author 九天芸菲
 */
public class AfterSaleLogUtils {

    public static void setAfterSaleInfo(Long id, Integer beforeStatus, Integer afterStatus) {
        setAfterSaleInfo(id, beforeStatus, afterStatus, null);
    }

    public static void setAfterSaleInfo(Long id, Integer beforeStatus, Integer afterStatus,
                                        Map<String, Object> exts) {
        AfterSaleLogAspect.setAfterSale(id, beforeStatus, afterStatus, exts);
    }

}
