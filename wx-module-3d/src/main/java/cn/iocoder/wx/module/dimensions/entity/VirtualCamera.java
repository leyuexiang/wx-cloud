package cn.iocoder.wx.module.dimensions.entity;


import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(autoResultMap = true)
@Schema(description = "虚拟相机表信息")
public class VirtualCamera implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "场景ID")
    private Integer sceneId;

    @Schema(description = "外部虚拟相机ID")
    private Integer cameraExternalId;

    @Schema(description = "相机名称")
    private String cameraName;

    @Schema(description = "相机坐标")
    private String vcPos;

    @Schema(description = "相机旋转")
    private String vcRotation;

    @Schema(description = "相机权重")
    private String vcPriority;

    @Schema(description ="是否跟随")
    private Boolean isFollowing;

    @Schema(description = "相机跟随")
    private String vcFollowObject;

    @Schema(description = "跟随路径偏移")
    private String vcFollowOffset;

    @Schema(description = "朝向")
    private String vcLookAt;


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
