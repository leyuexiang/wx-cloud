package cn.iocoder.wx.module.dimensions.domain.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "项目资源新建Vo表")
public class ProjectResourceInfoCreateVO {

    private Integer id;

    @Schema(description = "平台类型:'BIM','BABYLON_WEB','U3D_WEB','U3D'")
    private String resFromPlatform;

    @Schema(description = "项目id")
    private String projectId;

    @Schema(description = "资源类型")
    private String artResType;

    @Schema(description = "资源版本号ID")
    private Integer artResVersionId;

    @Schema(description = "资源在文件服务器上相对路径")
    private String artResRelativePath;

    @Schema(description = "资源类型id")
    private Integer typeId;

    @Schema(description = "资源名称")
    private String resourceName;

}
