package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "材质表信息")
public class MaterialVo {

    @Schema(description = "名字")
    private String materialName;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "图片相关信息")
    private String previews;

    @Schema(description = "多图片信息数组")
    private String maps;

}
