package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "组件信息")
public class ComponentInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "项目ID")
    private Integer projectId;

    @Schema(description = "组件唯一标识")
    private String componentUniqueId;

    @Schema(description = "组件名称")
    private String componentName;

    @Schema(description = "组件展示名称")
    private String componentShowName;

    @Schema(description = "组件类型")
    private Integer componentType;

    @Schema(description = "组件预制名")
    private String componentPrefabName;

    @Schema(description = "组件预制相对路径")
    private String componentAssetBundelName;

    @Schema(description = "根目录")
    private String componentAssetBundelRootDir;

    @Schema(description = "组件程序集名称")
    private String componentDllName;

    @Schema(description = "组件程序集根路径")
    private String componentDllRootDir;

    @Schema(description = "组件规则JSON信息")
    @TableField(value = "component_rule",fill = FieldFill.INSERT_UPDATE)
    private String componentRule;

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
    @TableField(value = "app_id",fill = FieldFill.INSERT_UPDATE)
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
