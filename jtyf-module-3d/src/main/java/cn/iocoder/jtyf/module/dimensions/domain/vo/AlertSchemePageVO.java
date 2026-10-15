package cn.iocoder.jtyf.module.dimensions.domain.vo;

import cn.iocoder.jtyf.module.dimensions.entity.AlertScheme;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "返回的预警方案分页实体信息")
public class AlertSchemePageVO {

    @Schema(description = "总条数",type = "integer")
    private Integer countMax;

    @Schema(description = "总页数",type = "integer")
    private Integer pageMax;

    @Schema(description = "预警方案信息列表")
    private List<AlertScheme> alertSchemeList;
}
