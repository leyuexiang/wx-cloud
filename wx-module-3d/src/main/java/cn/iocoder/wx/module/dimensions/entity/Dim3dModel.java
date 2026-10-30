package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 新模型资源实体，对应 wx_dim3d_model 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wx_dim3d_model")
@Schema(description = "3D模型资源")
public class Dim3dModel extends Dim3dResourceBaseEntity {
    @NotBlank @Size(min = 2, max = 50) private String modelName;
    @Size(max = 500) private String modelDesc;
    @NotBlank private String category;
    @NotBlank @Pattern(regexp = "SYSTEM|MINE") private String resourceBelong = "SYSTEM";
    @NotBlank @Pattern(regexp = "FREE|PAID") private String isPaid = "FREE";
    private Integer price;
    private Long coverImageId;
    @NotNull private Long modelFileId;
    private String status = "ONLINE";
}
