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
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "组件热力图表")
public class ComponentHeatMap implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Schema(description = "组件信息ID")
    private String componentInfoId;
    @Schema(description = "名字")
    private String name;
    @Schema(description = "热力点")
    private String heatPos;
    @Schema(description = "热力强度")
    private String intensity;
    @Schema(description = "坐标类型")
    private String posType;

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
