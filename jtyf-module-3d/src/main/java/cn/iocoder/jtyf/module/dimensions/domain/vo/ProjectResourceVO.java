package cn.iocoder.jtyf.module.dimensions.domain.vo;

import cn.iocoder.jtyf.module.dimensions.entity.Resource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目资源表")
public class ProjectResourceVO {

    public ProjectResourceVO(Resource resource){
        this.artResName= resource.getArtResName();
        this.artResType= resource.getArtResType();
        this.previewOutPath= resource.getPreviewOutPath();
        this.assetBundelName= resource.getAssetBundelName();
        this.assetName= resource.getAssetName();
        this.uniqueId= resource.getUniqueId();
        this.resFromPlatform= resource.getResFromPlatform();
        this.projectId= resource.getProjectId();
        this.rootDirName= resource.getRootDirName();
    }

    private String artResName;

    private String artResType;

    private String assetBundelName;

    private String assetName;

    private String previewOutPath;

    private String uniqueId;

    private String resFromPlatform;

    @Schema(description = "资源项目Id")
    private String projectId;

    @Schema(description = "文件根目录")
    private String rootDirName;
}
