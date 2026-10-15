package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "组件信息分页实体")
public class ComponentInfoVO {

    @Schema(description = "组件名称")
    private String componentName;

    @Schema(description = "组件类型")
    private Integer componentType;

    @Schema(description = "条数")
    private Integer size;

    @Schema(description = "页数")
    private Integer page;
}
