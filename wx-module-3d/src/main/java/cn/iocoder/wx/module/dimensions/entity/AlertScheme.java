package cn.iocoder.wx.module.dimensions.entity;


import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.*;
import cn.iocoder.wx.module.dimensions.config.ObjectJsonHandler;
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
@Schema(description = "预警方案表信息")
public class AlertScheme implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "预警名称")
    private String schemeName;

    @Schema(description  = "预警类型-0：模型范围光提示（U3D用）1：全局弹窗提示（WEB用）")
    private Integer alertType;

    @Schema(description = "颜色-JSON数据结构")
    @TableField(value = "color",typeHandler = ObjectJsonHandler.class)
    private JSONObject color;

    @Schema(description = "位置")
    private String location;

    @Schema(description = "文本样式")
    private String textStyle;

    @Schema(description = "显示方式-0：直接显示;1:鼠标移入显示")
    private Integer displayMode;

    @Schema(description ="预警文本")
    private String alertText;

    @Schema(description = "唯一标识")
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
