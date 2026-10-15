package cn.iocoder.jtyf.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 新资源表公共字段。
 * 统一承载租户、归属和审计字段，避免三个目录资源实体重复声明和漏填字段。
 */
@Data
public abstract class Dim3dResourceBaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dataUserId;
    private Long dataDeptId;
    private String tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createDept;
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 逻辑删除标志，删除接口只更新该字段，避免资源被场景引用时丢失审计记录。 */
    @TableLogic
    private String delFlag;
}
