package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
@Schema(description = "项目场景信息发布VO表")
public class ProjectEngineeringPublishVO {

    @Schema(description = "数据库id")
    private Integer id;

    @Schema(description = "是否发布0未发布 1发布")
    private Integer isPublish;
}
