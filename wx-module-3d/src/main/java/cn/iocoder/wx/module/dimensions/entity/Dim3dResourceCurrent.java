package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 稳定资源的当前内容持久化模型，复用原转换资源的基础字段。
 * 新接口必须使用独立请求对象；不得把持久化模型作为可任意赋值的上传参数。
 * 旧转换接口继续使用父类，避免外部转换回执覆盖范围、摘要和并发修订。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wx_dim3d_resource")
public class Dim3dResourceCurrent extends Dim3dConvertedResource {
    /** NULL 表示未分类旧数据，新消费查询必须排除。 */
    private String resourceScope;
    /** LEGACY、PENDING、READY；替换期间保持旧 READY 内容可读。 */
    private String contentState;
    private String storagePrefix;
    private String manifestPath;
    private String manifestSha256;
    private String contentHash;
    private String contentFormat;
    private String platform;
    private String appCompatibility;
    /** 内部乐观并发标记；接口按十进制字符串传输，不是用户可选版本。 */
    private Long currentRevision;
    private String currentOperationId;
}
