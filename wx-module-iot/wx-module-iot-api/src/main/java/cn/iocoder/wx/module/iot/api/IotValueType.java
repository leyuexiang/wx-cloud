package cn.iocoder.wx.module.iot.api;

/** 遥测值的标准化类型，避免规则引擎重复猜测外部数据类型。 */
public enum IotValueType {

    NUMBER,
    BOOLEAN,
    STRING,
    JSON
}
