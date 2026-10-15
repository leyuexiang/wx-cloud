package cn.iocoder.jtyf.module.dimensions.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IsPublishVO {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数据库id")
    private Integer id;

    @Schema(description = "是否发布0未发布 1发布")
    private Integer isPublish;

    @Schema(description = "平台类型")
    private String platformType;

    @Schema(description = "已发布场景的url路径")
    private String url;
}
