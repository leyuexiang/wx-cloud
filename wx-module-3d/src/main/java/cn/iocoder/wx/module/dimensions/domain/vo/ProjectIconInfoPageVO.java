package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.IconInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "项目图标分页表")
public class ProjectIconInfoPageVO {

    @Schema(description = "总条数")
    private Integer countMax;

    @Schema(description = "总页数")
    private Integer pageMax;

    @Schema(description = "图标列表")
    private List<IconInfo> projectIconInfoList;
}
