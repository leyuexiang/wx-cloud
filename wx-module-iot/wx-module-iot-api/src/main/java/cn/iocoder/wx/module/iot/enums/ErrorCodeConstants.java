package cn.iocoder.wx.module.iot.enums;

import cn.iocoder.wx.framework.common.exception.ErrorCode;

/** 物联网模块业务错误码。 */
public interface ErrorCodeConstants {

    ErrorCode CONNECTOR_NOT_EXISTS = new ErrorCode(1_009_001_001, "物联网连接器不存在");
    ErrorCode CONNECTOR_ID_DUPLICATE = new ErrorCode(1_009_001_002, "物联网连接器编号已存在");
    ErrorCode CONNECTOR_TYPE_INVALID = new ErrorCode(1_009_001_003, "物联网连接器协议类型不支持");
    ErrorCode POINT_MAPPING_INVALID = new ErrorCode(1_009_001_004, "物联网点位映射配置无效");
    ErrorCode INBOX_EVENT_NOT_EXISTS = new ErrorCode(1_009_001_005, "物联网接入事件不存在");
    ErrorCode INBOX_REPLAY_NOT_ALLOWED = new ErrorCode(1_009_001_006, "当前物联网接入事件不允许重放");
    ErrorCode INBOX_PAYLOAD_INVALID = new ErrorCode(1_009_001_007, "物联网接入事件载荷无效");
    ErrorCode WEBHOOK_CONNECTOR_INVALID = new ErrorCode(1_009_001_008, "HTTPS 连接器未启用或类型不匹配");
    ErrorCode WEBHOOK_SIGNATURE_INVALID = new ErrorCode(1_009_001_009, "物联网回调签名无效");
    ErrorCode WEBHOOK_PAYLOAD_INVALID = new ErrorCode(1_009_001_010, "物联网回调报文无效");
    ErrorCode DEVICE_BINDING_NOT_EXISTS = new ErrorCode(1_009_001_101, "物联网设备绑定不存在");
    ErrorCode DEVICE_BINDING_DUPLICATE = new ErrorCode(1_009_001_102, "外部设备绑定已存在");
}
