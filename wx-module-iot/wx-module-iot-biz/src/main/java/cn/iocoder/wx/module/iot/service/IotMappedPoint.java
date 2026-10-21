package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.IotValueType;

/** 点位映射后的标准值。 */
public record IotMappedPoint(String pointCode, Object value, IotValueType valueType, String unit) {
}
