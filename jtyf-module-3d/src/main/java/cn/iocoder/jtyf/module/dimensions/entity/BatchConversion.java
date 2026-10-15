package cn.iocoder.jtyf.module.dimensions.entity;

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
@NoArgsConstructor
@AllArgsConstructor
public class BatchConversion implements Serializable {

    private static final long serialVersionUID = 10L;

    @TableId(value = "id",type = IdType.AUTO)
    private Integer id;

    @Schema(description = "资源前端显示名称")
    private String resourceWebName;

    @Schema(description = "资源类型" )
    private String resourceType;

    @Schema(description = "待下载资源目录名称" )
    private String resourceDirectoryName;

    @Schema(description = "上传压缩包名字-前端显示" )
    private String uploadFileName;

    @Schema(description = "上传压缩包名字-唯一处理" )
    private String uniqueUploadFileName;

    @Schema(description = "资源大小" )
    private Double resourceSize;

    @Schema(description = "资源大小" )
    private String conversionStatus;

    @Schema(description = "登陆用户名" )
    private String userName;

    @Schema(description = "上传时间")
    @TableField(value = "upload_time",fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;

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
