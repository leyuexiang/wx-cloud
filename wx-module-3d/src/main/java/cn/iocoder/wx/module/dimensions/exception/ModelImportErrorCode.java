package cn.iocoder.wx.module.dimensions.exception;

import cn.iocoder.wx.framework.common.exception.ErrorInfo;

/** 模型导入固定错误，工具诊断只用于受控内部排障，不作为响应正文。 */
public enum ModelImportErrorCode implements ErrorInfo {
    SOURCE_MISSING(2030001100,"原始模型尚未完整上传",false),
    SOURCE_CHECKSUM_MISMATCH(2030001101,"原始模型大小或摘要不匹配",false),
    UNSUPPORTED_INPUT(2030001102,"不支持该模型输入",false),
    CONVERTER_BUSY(2030001103,"模型转换服务繁忙，可稍后重试",true),
    CONVERTER_TIMEOUT(2030001104,"模型转换超时，可稍后重试",true),
    CONVERTER_UNAVAILABLE(2030001105,"模型转换服务暂不可用",true),
    INVALID_MODEL(2030001106,"模型产物未通过校验",false),
    ARTIFACT_UNAVAILABLE(2030001107,"固定产物不可用，不能重新转换覆盖",false),
    IMPORT_PERMISSION_REVOKED(2030001108,"模型导入维护权限已失效",false),
    SOURCE_STORAGE_UNAVAILABLE(2030001109,"原始模型存储暂不可用",true),
    ARTIFACT_STORAGE_UNAVAILABLE(2030001110,"模型转换产物存储暂不可用，可稍后重试",true),
    DIRECTORY_INVALID(2030001111,"模型分类或封面已失效，请重新确认目录信息",false);

    private final int code;
    private final String message;
    private final boolean retryable;
    ModelImportErrorCode(int code,String message,boolean retryable) {
        this.code=code;this.message=message;this.retryable=retryable;
    }
    @Override public Integer getCode() { return code; }
    @Override public String getMsg() { return message; }
    public boolean isRetryable() { return retryable; }
}
