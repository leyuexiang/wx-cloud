package cn.iocoder.jtyf.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

import static cn.iocoder.jtyf.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;
import static cn.iocoder.jtyf.framework.common.util.date.DateUtils.TIME_ZONE_DEFAULT;


@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "项目资源表")
public class Resource extends AbstractEntity<Integer> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    @Schema(description = "资源文件目录名称")
    private String artResName;

    @Schema(description = "资源类型")
    private String artResType;

    @Schema(description = "资源文件相对路径(art_res_name/asset_name）")
    private String assetBundelName;

    @Schema(description = "资源文件绝对路径")
    private String previewPath;

    @Schema(description = "资源文件名称")
    private String assetName;

    @Schema(description = "String")
    private String previewOutPath;

    @Schema(description = "String")
    private String uniqueId;

    @Schema(description = "String")
    private String keyId;

    @Schema(description = "资源项目Id")
    private String projectId;

    @Schema(description = "是否收藏")
    private Integer isCollection;

    @Schema(description = "资源类型id")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer typeId;

    @Schema(description = "资源类型名称")
    private String typeName;

    @Schema(description = "资源名称")
    private String resourceName;

    @Schema(description = "上传时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING,pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    private LocalDateTime uploadTime;

    @Schema(description = "上传人名字")
    private String uploadUserName;

    @Schema(description = "上传人Id")
    private Integer uploadUserId;

    @Schema(description = "展示图地址")
    private String bannerAddress;

    @Schema(description = "展示图名字")
    private String bannerName;

    @Schema(description = "上传渠道")
    private String resFromPlatform;

    @Schema(description = "修改时间")
    @TableField(value = "update_time",fill = FieldFill.UPDATE)
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    private LocalDateTime updateTime;

    @Schema(description = "资源数据类型")
    private String resourceDataType;

    @Schema(description = "资源数据类型id")
    private Integer resourceDataTypeId;

    @Schema(description = "文件根目录")
    private String rootDirName;

    @Schema(description = "gif图路径")
    private String gifPath;

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
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @JsonFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND, timezone = TIME_ZONE_DEFAULT)
    private LocalDateTime createTime;

    @Schema(description = "创建者Id")
    @TableField(value = "created_by",fill = FieldFill.INSERT)
    private Integer createdBy;

    @Schema(description = "更新者Id")
    @TableField(value = "last_modified_by",fill = FieldFill.INSERT_UPDATE)
    private Integer lastModifiedBy;
}
