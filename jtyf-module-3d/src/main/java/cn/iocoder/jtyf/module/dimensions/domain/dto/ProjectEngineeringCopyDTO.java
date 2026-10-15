package cn.iocoder.jtyf.module.dimensions.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectEngineeringCopyDTO {
    private Integer id;

    @Schema(description = "场景名称")
    private String name;
}
