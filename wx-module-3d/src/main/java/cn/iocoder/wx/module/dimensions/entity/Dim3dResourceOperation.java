package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单资源操作、幂等结果和恢复快照；不是业务资源版本表。
 * 快照保留规范 JSON，不保存签名地址、认证令牌或存储密钥。
 * 全局记录的 tenantId 为字符串 0，必须由专用且显式带范围条件的服务访问。
 */
@Data
@TableName("wx_dim3d_resource_operation")
public class Dim3dResourceOperation {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long resourceId;
    private String operationType;
    private String resourceScope;
    private String tenantId;
    private Long operatorId;
    private String requestHash;
    private Long expectedRevision;
    private Long committedRevision;
    private String status;
    private String storagePrefix;
    private String candidateDescriptor;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String errorCode;
    /** 业务接口约定 UTC，返回时显式转换为带 Z 的时间。 */
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
