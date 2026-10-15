package cn.iocoder.wx.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件位置&名称信息VO")
public class MinioFileVO {
    @Schema(description = "bucket名称")
    private String bucket;

    @Schema(description = "文件名称")
    private String objectName;
}
