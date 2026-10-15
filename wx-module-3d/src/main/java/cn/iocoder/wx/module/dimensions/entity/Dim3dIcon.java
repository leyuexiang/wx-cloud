package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 新图标资源实体，对应 dim3d_icon 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dim3d_icon")
public class Dim3dIcon extends Dim3dResourceBaseEntity {
    @NotBlank @Size(min = 2, max = 50) private String iconName;
    @Size(max = 500) private String iconDesc;
    @NotBlank private String category;
    private String secondaryCategory;
    @NotBlank @Pattern(regexp = "SYSTEM|MINE") private String resourceBelong = "SYSTEM";
    @NotNull private Long iconFileId;
    private String status = "ONLINE";
}
