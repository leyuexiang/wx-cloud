package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description="预警方案分页实体")
public class AlertSchemePage {

    @Schema(description = "预警名称")
    private String schemeName;

    @Schema(description = "条数",type = "integer")
    private Integer size;

    @Schema(description = "页数",type = "integer")
    private Integer page;
}
