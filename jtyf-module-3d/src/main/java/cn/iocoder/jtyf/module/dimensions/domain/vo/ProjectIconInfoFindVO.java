package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "项目图标条件查询Vo表")
public class ProjectIconInfoFindVO {

    @Schema(description = "是否收藏",type = "integer")
    private Integer isCollection;

    @Schema(description = "图标类型id",type = "integer")
    private Integer typeId;

    @Schema(description = "图标名称")
    private String iconName;

    @Schema(description = "平台类型:'BIM','BABYLON_WEB','U3D_WEB','U3D','U3D_ALL'")
    @NotBlank(message = "平台类型不能为空")
    private String resFromPlatform;

    @Schema(type = "integer",description = "排序规则1最后更新时间升序,2最后更新时间降序")
    private Integer sort;

    @Schema(description = "页数",type = "integer")
    private Integer page;

    @Schema(description = "长度",type = "integer")
    private Integer size;




}
