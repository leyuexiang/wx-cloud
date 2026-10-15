package cn.iocoder.wx.module.dimensions.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
public class MaterialBaseDTO {

    @Schema(description = "图片名称")
    private String textureName;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "分辨率")
    private String resolution;

    @Schema(description = "Base64加密")
    private String relativePath;
}
