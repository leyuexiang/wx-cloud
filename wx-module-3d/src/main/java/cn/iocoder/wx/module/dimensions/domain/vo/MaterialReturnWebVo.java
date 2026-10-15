package cn.iocoder.wx.module.dimensions.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "材质表信息-给前端")
public class MaterialReturnWebVo  {

    @Schema(description = "总条数")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
    private Integer countMax;

    @Schema(description = "总页数")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
    private Integer pageMax;

    @Schema(description = "材质列表")
    private List<MaterialWebVo> MaterialWebVos;

}
