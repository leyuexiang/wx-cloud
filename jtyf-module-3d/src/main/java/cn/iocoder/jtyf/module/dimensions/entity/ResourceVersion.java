package cn.iocoder.jtyf.module.dimensions.entity;


import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "项目资源版本表")
public class ResourceVersion extends AbstractEntity<Integer> {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "版本号")
    private String versionName;

    @Schema(description = "创建者")
    @TableField(value = "create_user",fill = FieldFill.INSERT)
    private String createUser;

    @Schema(description = "更新人")
    @TableField(value = "update_user",fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Schema(description = "创建时间")
    @TableField(value = "create_time",fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(value = "update_time",fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(description = "创建者Id")
    @TableField(value = "created_by",fill = FieldFill.INSERT)
    private Integer createdBy;

    @Schema(description = "更新者Id")
    @TableField(value = "last_modified_by",fill = FieldFill.INSERT_UPDATE)
    private Integer lastModifiedBy;
}

