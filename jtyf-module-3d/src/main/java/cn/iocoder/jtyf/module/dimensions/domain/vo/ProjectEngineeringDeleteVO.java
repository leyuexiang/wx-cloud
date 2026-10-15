package cn.iocoder.jtyf.module.dimensions.domain.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "场景删除VO表")
public class ProjectEngineeringDeleteVO {

    @Schema(description = "数据库id")
    private Integer id;
}
