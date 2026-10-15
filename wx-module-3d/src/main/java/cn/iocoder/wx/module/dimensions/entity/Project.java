package cn.iocoder.wx.module.dimensions.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "项目实体")
public class Project implements Serializable {

    @Schema(description = "项目名称")
    private Long appId;

    @Schema(description = "项目名称")
    private String projectName;


}