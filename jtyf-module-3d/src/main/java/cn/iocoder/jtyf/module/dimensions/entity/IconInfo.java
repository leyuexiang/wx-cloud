package cn.iocoder.jtyf.module.dimensions.entity;


import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "图标信息表")
public class IconInfo extends AbstractEntity<Integer> {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "项目id")
    @TableField(value = "project_id",fill = FieldFill.INSERT)
    private String projectId;

    @Schema(description = "资源唯一标识")
    @TableField(value = "unique_id",fill = FieldFill.INSERT)
    private String uniqueId;

    @Schema(description = "图标名称")
    @NotBlank(message = "图标名称不能为空")
    @Size(min = 1, max = 30, message = "图标名称长度需在1-30个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-]+$", message = "图标名称只能包含中文、英文、数字、下划线和短横线")
    private String iconName;

    @Schema(description = "图标路径")
    private String iconPath;

    @Schema(description = "平台类型:'BIM','BABYLON_WEB','U3D_WEB','U3D'")
    @TableField(value = "res_from_platform",fill = FieldFill.INSERT)
    @NotBlank(message = "平台类型不能为空")
    private String resFromPlatform;

    @Schema(description = "是否收藏")
    private Integer isCollection;

    @Schema(description = "图标类型id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer typeId;

    @Schema(description = "图标类型名称")
    private String typeName;

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

