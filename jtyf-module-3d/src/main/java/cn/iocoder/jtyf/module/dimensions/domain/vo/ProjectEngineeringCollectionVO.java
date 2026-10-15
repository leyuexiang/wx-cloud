package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目场景信息收藏VO表")
public class ProjectEngineeringCollectionVO {

    @Schema(description = "数据库id")
    private Integer id;

//    @ApiModelProperty(value = "是否收藏0否 1是",dataType = "Integer")
//    private Integer isCollection;
}
