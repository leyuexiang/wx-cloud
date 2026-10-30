package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 新模板资源实体，对应 wx_dim3d_template 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wx_dim3d_template")
public class Dim3dTemplate extends Dim3dResourceBaseEntity {
    @NotBlank @Size(min = 2, max = 50) private String templateName;
    @Size(max = 500) private String templateDesc;
    @NotBlank @Pattern(regexp = "INDUSTRY|EXHIBITION") private String templateType = "INDUSTRY";
    @NotBlank private String category;
    @NotBlank @Pattern(regexp = "SYSTEM|MINE") private String resourceBelong = "SYSTEM";
    private Long coverImageId;
    @NotNull private Long templateFileId;
    private String status = "ONLINE";
}
