package cn.iocoder.wx.module.infra.service.file;

import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;

/** 文件元数据已提交且 GET 地址已签发后的内部结果；地址和对象键只用于短期响应/本次补偿。 */
public record OwnedImageUploadResult(FileDO file, String readUrl) {
}
