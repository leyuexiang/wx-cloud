package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@Schema(description = "场景VO表")
public class ProjectEngineeringUpdateVO {

    @NotNull(message = "场景ID不可为空")
    @Schema(description = "数据库id")
    private Integer id;

    @NotBlank(message = "平台类型不可为空")
    @Schema(description = "平台类型;3d-web,3d-unity,3d-ue")
    private String platformType;

    @Schema(description = "组件")
    private String comsInstance;

    @Schema(description = "场景配置信息，babylon用")
    private String sceneConfigInfo;

    @Schema(description = "图标信息列表")
    private String iconInfos;
}
