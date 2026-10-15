package cn.iocoder.jtyf.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "文件唯一信息表")
public class FileOnlyInfo extends AbstractEntity<Integer> {
    private Integer id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "上一级id")
    private Integer parentId;

    @Schema(description = "上一级名称")
    private String parentName;

    @Schema(description = "项目Id")
    private String projectId;

    @Schema(description = "文件码")
    private String fileCode;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "文件的唯一号,目录及子文件一致")
    private String uniqueId;

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
