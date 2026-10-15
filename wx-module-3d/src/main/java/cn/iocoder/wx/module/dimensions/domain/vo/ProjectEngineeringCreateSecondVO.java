package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "名称")
public class ProjectEngineeringCreateSecondVO {
    @Schema(description = "名称")
    private String name;
    @Schema(description = "是否直接发布0不发布 1发布")
    private Integer isPublish;
}
