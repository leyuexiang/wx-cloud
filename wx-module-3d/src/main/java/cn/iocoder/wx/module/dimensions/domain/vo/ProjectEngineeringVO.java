package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * @Description: 项目工程模块信息表
 * @author jianlei
 * @since 2022-12-01 17:13
 * @version 1.0
 */
@Data
@Schema(description = "项目场景信息VO表")
public class ProjectEngineeringVO {
    @Schema(description = "数据库id")
    private Integer id;

    @Schema(description = "gisInfo")
    private Object gisInfo;

    @Schema(description = "项目")
    private String project;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "节点")
    private Object nodes;

    @Schema(description = "组件")
    private Object comsInstance;

    @Schema(description = "是否公开")
    private Integer isPublish;

    @Schema(description = "当前版本号 每修改一次增加1")
    private Integer version;

    @Schema(description = "状态 0 删除 1正常 2旧版本(修改遗留)")
    private Integer status;

    @Schema(description = "状态信息")
    private Object stateInfos;

    @Schema(description = "releaseStateUuid")
    private String releaseStateUuid;

    @Schema(description = "锁定的节点列表")
    private Object lockedGameobjectList;

    @Schema(description = "摄像机初始位置")
    private Object cameraInitialPosition;

    @Schema(description = "摄像机初始角度")
    private Object cameraInitialEuler;

    @Schema(description = "公共组件属性列表")
    private Object commComJsons;

    @Schema(description = "U3D唯一ID")
    private String sceneResId;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "编辑人")
    private String editor;

    @Schema(description = "组件信息集合")
    private Object comPoints;

    @Schema(description = "组件-身上的脚本集合")
    private Object hotfixComponents;

    @Schema(description = "组件-实例信息")
    private Object dicComSystemInfo;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "场景图片")
    private String photoAddress;

    @Schema(description = "场景配置信息，babylon用")
    private Object sceneConfigInfo;

    @Schema(description = "图标信息列表")
    private Object iconInfos;

}
