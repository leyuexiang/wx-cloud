package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "材质表分页实体信息")
public class MaterialPageVo {

    @Schema(description = "名字")
    private String materialName;

    @Schema(description = "类型id")
    private Integer typeId;

    @Schema(description = "页数")
    private Integer page;

    @Schema(description = "长度")
    private Integer size;
}
