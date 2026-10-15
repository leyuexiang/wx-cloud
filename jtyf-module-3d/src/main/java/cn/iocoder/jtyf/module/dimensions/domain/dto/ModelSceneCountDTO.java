package cn.iocoder.jtyf.module.dimensions.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelSceneCountDTO {

    @Schema(description = "模型数量")
    private Integer modelSize;

    @Schema(description = "场景数量")
    private Integer sceneSize;
}
