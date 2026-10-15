package cn.iocoder.jtyf.module.dimensions.entity;


import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

import static cn.iocoder.jtyf.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;
import static cn.iocoder.jtyf.framework.common.util.date.DateUtils.TIME_ZONE_DEFAULT;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "项目资源基础信息表")
public class ResourceInfo extends AbstractEntity<Integer> implements Serializable  {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "平台类型:'BIM','BABYLON_WEB','U3D_WEB','U3D'")
    @TableField(value = "res_from_platform",fill = FieldFill.INSERT)
    @NotBlank(message = "平台类型不能为空")
    private String resFromPlatform;

    @Schema(description = "创建者Id")
    @TableField(value = "created_by",fill = FieldFill.INSERT)
    private Integer createdBy;

    @Schema(description = "创建者")
    @TableField(value = "create_user",fill = FieldFill.INSERT)
    private String createUser;

    @Schema(description = "创建时间")
    @TableField(value = "create_time",fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    private LocalDateTime createTime;

    @Schema(description = "更新者Id")
    @TableField(value = "last_modified_by",fill = FieldFill.INSERT_UPDATE)
    private Integer lastModifiedBy;

    @Schema(description = "更新人")
    @TableField(value = "update_user",fill = FieldFill.INSERT_UPDATE)
    private String updateUser;

    @Schema(description = "更新时间")
    @TableField(value = "update_time",fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    private LocalDateTime updateTime;

    @Schema(description = "项目id")
    @TableField(value = "project_id",fill = FieldFill.INSERT)
    private String projectId;

    @Schema(description = "资源名称")
    @TableField(value = "art_res_name",fill = FieldFill.INSERT)
    private String artResName;

    @Schema(description = "资源类型")
    @TableField(value = "art_res_type",fill = FieldFill.INSERT)
    private String artResType;

    @Schema(description = "资源版本号ID")
    @TableField(value = "art_res_version_id",fill = FieldFill.INSERT)
    private Integer artResVersionId;

    @Schema(description = "资源版本号")
    @TableField(value = "art_res_version",fill = FieldFill.INSERT)
    private String artResVersion;

    @Schema(description = "AB资源相对路径")
    @TableField(value = "asset_bundel_name",fill = FieldFill.INSERT)
    private String assetBundelName;

    @Schema(description = "资源预制体名称")
    @TableField(value = "asset_name",fill = FieldFill.INSERT)
    private String assetName;

    @Schema(description = "资源唯一标识")
    @TableField(value = "unique_id",fill = FieldFill.INSERT)
    private String uniqueId;

    @Schema(description = "资源在文件服务器上相对路径")
    @TableField(value = "art_res_relative_path",fill = FieldFill.INSERT)
    private String artResRelativePath;


    @Schema(description = "美术资源原始JSON信息")
    @TableField(value = "art_res_raw_data",fill = FieldFill.INSERT_UPDATE)
    private String artResRawData;

    @Schema(description = "是否收藏")
    private Integer isCollection;

    @Schema(description = "资源类型id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer typeId;

    @Schema(description = "资源类型名称")
    private String typeName;

    @Schema(description = "资源名称（支持中文、英文、数字、下划线、短横线和点号，长度1-50个字符）")
    @NotBlank(message = "资源名称不能为空或仅包含空格")
    @Size(min = 1, max = 50, message = "资源名称长度必须在1-50个字符之间（中文、英文等均计为1个字符）")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-.]+$", message = "资源名称仅支持中文、英文、数字、下划线（_）、短横线（-）和点号（.）")
    private String resourceName;

    @Schema(description = "展示图地址")
    private String bannerAddress;

    @Schema(description = "展示图名字")
    private String bannerName;

    @Schema(description = "美术资源文件根目录")
    private String rootDirName;

}

