package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "文件信息表")
@TableName(value = "file_info")
public class FileInfo extends AbstractEntity<Integer> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件等级，在多少层")
    private Integer fileLevel;

    @Schema(description = "文件类型 0文件夹 1文件")
    private Integer fileType;

    @Schema(description = "父文件id")
    private Integer parentId;

    @Schema(description = "文件大小")
    private String fileSize;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "上传用户Id")
    private Integer uploadUserId;

    @Schema(description = "上传用户名")
    private String uploadUserName;

    @Schema(description = "文件状态")
    @TableField("`status`")
    private Integer status;

    @Schema(description = "是否有下一层目录 0没有，1有")
    private Integer hasNext;

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
