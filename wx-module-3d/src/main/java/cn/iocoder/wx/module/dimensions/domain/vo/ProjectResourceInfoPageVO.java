package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.Resource;
import cn.iocoder.wx.module.dimensions.entity.ResourceInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "项目资源分页表")
public class ProjectResourceInfoPageVO {

    @Schema(description = "总条数")
    private Integer countMax;

    @Schema(description = "总页数")
    private Integer pageMax;

    @Schema(description = "资源列表")
    private List<ResourceInfo> projectResourceInfoList;
}
