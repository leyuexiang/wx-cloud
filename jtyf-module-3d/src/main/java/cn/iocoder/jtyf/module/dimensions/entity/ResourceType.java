package cn.iocoder.jtyf.module.dimensions.entity;


import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "项目资源类型表")
public class ResourceType extends AbstractEntity<Integer> {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "分类编码")
    private String typeCode;

    @Schema(description = "资源分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资源分类名称不能为空")
    @Size(min = 1, max = 30, message = "资源分类名称长度需在1-30个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-]+$", message = "资源分类名称只能包含中文、英文、数字、下划线和短横线")
    private String typeName;

    @Schema(description = "创建者")
    @TableField(value = "create_user",fill = FieldFill.INSERT)
    private String createUser;

    @Schema(description = "更新人")
    @TableField(value = "update_user",fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Schema(description = "组织机构id")
    @TableField(value = "org_id_path",fill = FieldFill.INSERT)
    private String orgIdPath;

    @Schema(description = "应用id")
    @TableField(value = "app_id",fill = FieldFill.INSERT)
    private Long appId;

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

