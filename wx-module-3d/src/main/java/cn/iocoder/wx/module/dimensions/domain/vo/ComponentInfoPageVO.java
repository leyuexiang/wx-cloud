package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.ComponentInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "返回的分页组件信息")
public class ComponentInfoPageVO {

    @Schema(description = "总条数")
    private Long countMax;

    @Schema(description = "总页数")
    private Long pageMax;

    @Schema(description = "组件信息列表")
    private List<ComponentInfo> componentInfoList;
}
