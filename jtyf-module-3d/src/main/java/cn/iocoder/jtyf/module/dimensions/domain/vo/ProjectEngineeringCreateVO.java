package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "场景VO表")
public class ProjectEngineeringCreateVO {

    @Schema(description = "数据库id")
    private Integer id;

    @Schema(description = "gisInfo")
    private Object gisInfo;

    @Schema(description = "项目")
    private String project;

    @Schema(description = "场景名称")
    @NotBlank(message = "场景名称不能为空")
    @Size(min = 1, max = 30, message = "场景名称长度需在1-30个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-]+$", message = "场景名称只能包含中文、英文、数字、下划线和短横线")
    private String name;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "节点")
    private Object nodes;

    @Schema(description = "组件")
    private Object comsInstance;

    @Schema(description = "状态信息")
    private Object stateInfos;

    @Schema(description = "releaseStateUuid")
    private String releaseStateUuid;

    @Schema(description = "锁定的节点列表")
    private List lockedGameobjectList;

    @Schema(description = "摄像机初始位置")
    private Object cameraInitialPosition;

    @Schema(description = "摄像机初始角度")
    private Object cameraInitialEuler;

    @Schema(description = "公共组件属性列表")
    private Object commComJsons;

    @Schema(description = "U3D唯一ID")
    private String sceneResId;

    @Schema(description = "组件信息集合")
    private Object comPoints;

    @Schema(description = "组件-身上的脚本集合")
    private Object hotfixComponents;

    @Schema(description = "组件-实例信息")
    private Object dicComSystemInfo;

    @Schema(description = "场景配置信息，babylon用")
    private Object sceneConfigInfo;

    @Schema(description = "图标信息列表")
    private Object iconInfos;


}
