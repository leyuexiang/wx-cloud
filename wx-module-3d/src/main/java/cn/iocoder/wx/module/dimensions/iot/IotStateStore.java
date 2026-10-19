package cn.iocoder.wx.module.dimensions.iot;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;

/** 显式租户键的最新点位状态存储，不依赖线程中的租户拦截开关。 */
public interface IotStateStore {
    /** 原子比较并写入更新的事件；重复或乱序事件返回 false，且不延长保留期。 */
    boolean putLatest(TwinTelemetryEvent event, long remainingTtlMillis);

    /** 仅查询指定租户、设备、点位，不提供全租户扫描入口。 */
    TwinTelemetryEvent get(Long tenantId, String deviceId, String pointCode);
}
