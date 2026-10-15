package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Schema(description = "场景上传/下载信息表")
public class SceneDef extends AbstractEntity<Integer> implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "gisInfo")
    private String gisInfo;

    @Schema(description = "项目编号")
    private String project;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型")
    private String type = "u3d";

    @Schema(description = "U3D唯一ID")
    private String sceneResId;

    @Schema(description = "节点")
    private String nodes;

    @Schema(description = "组件信息集合")
    private String comPoints;

    @Schema(description = "身上所有的组件")
    private String components;

    @Schema(description = "组件-身上的脚本集合")
    private String comPointComponents;

    @Schema(description = "锁定的节点列表")
    private String lockedGameObjectList;

    @Schema(description = "摄像机初始位置")
    private String cameraInitialPosition;

    @Schema(description = "摄像机初始角度")
    private String cameraInitialEuler;

    @Schema(description = "当前主状态")
    private String releaseStateUuid;

    @Schema(description = "字典")
    private String stateInfos;

    @Schema(description = "公共组件属性列表")
    private String commComJsons;

    @Schema(description = "是否公开")
    private Integer isPublish;

    @Schema(description = "当前版本do号 每更新一次增加1")
    private Integer version;

    @Schema(description = "状态 0 删除 1正常 2旧版本")
    private Integer status;

    @Schema(description = "修改时间")
    @TableField(value = "update_time",fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

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

    @Schema(description = "创建者Id")
    @TableField(value = "created_by",fill = FieldFill.INSERT)
    private Integer createdBy;

    @Schema(description = "更新者Id")
    @TableField(value = "last_modified_by",fill = FieldFill.INSERT_UPDATE)
    private Integer lastModifiedBy;
}

