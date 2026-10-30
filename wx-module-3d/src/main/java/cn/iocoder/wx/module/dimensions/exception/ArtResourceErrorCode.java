package cn.iocoder.wx.module.dimensions.exception;

import cn.iocoder.wx.framework.common.exception.ErrorInfo;

/** ART-01 固定的新资源接口错误合同；由 ART-02/03 使用，不改变旧接口错误码。 */
public enum ArtResourceErrorCode implements ErrorInfo {
    NOT_VISIBLE(2030001001, "资源不存在或不可见"),
    ADMIN_REQUIRED(2030001002, "当前身份不能维护此范围的资源"),
    REVISION_CONFLICT(2030001003, "资源已被其他操作更新，请刷新后重试"),
    IDEMPOTENCY_CONFLICT(2030001004, "同一操作编号不能提交不同内容"),
    INCOMPATIBLE_PLAYER(2030001005, "资源与当前运行包不兼容"),
    INVALID_CLOSURE(2030001006, "资源依赖或灯光关联不完整"),
    OBJECT_MISSING(2030001007, "资源对象尚未完整上传"),
    CHECKSUM_MISMATCH(2030001008, "资源大小或摘要校验失败"),
    OPERATION_EXPIRED(2030001009, "资源操作已过期，请创建新操作"),
    RESOURCE_DELETED(2030001010, "资源已删除，不能继续替换"),
    UNSAFE_PATH(2030001011, "资源路径不在本次操作允许的范围内"),
    INVALID_OPERATION_STATE(2030001012, "当前操作状态不允许此步骤"),
    LEGACY_UNCLASSIFIED(2030001013, "旧资源尚未完成范围与内容分类"),
    STORAGE_UNAVAILABLE(2030001014, "受控资源存储暂不可用"),
    RETRYABLE_DEPENDENCY(2030001015, "依赖服务暂不可用，可查询结果后重试"),
    SNAPSHOT_UNAVAILABLE(2030001016, "恢复快照不可用或不属于此资源"),
    SCOPE_CHANGE_FORBIDDEN(2030001017, "替换不能改变资源范围或租户"),
    INVALID_TENANT(2030001018, "认证租户与资源归属不一致"),
    SCHEMA_NOT_READY(2030001019, "资源数据结构尚未就绪");

    private final int code;
    private final String message;

    ArtResourceErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public Integer getCode() { return code; }
    @Override public String getMsg() { return message; }
}
