package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目资源表")
public class projectResourceU3DVO {

    private String artResName;

    private String artResType;

    private String assetBundelName;

    private String assetName;

    private String previewOutPath;

    private String uniqueId;

    private String resFromPlatform;

    @Schema(description = "资源项目Id")
    private String projectId;

    @Schema(description = "路径")
    private String uploadPath;

    @Schema(description = "文件根目录")
    private String rootDirName;
}
