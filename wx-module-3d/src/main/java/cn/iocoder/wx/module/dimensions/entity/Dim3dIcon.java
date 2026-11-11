package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 新图标资源实体，对应 wx_3d_icon 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wx_3d_icon")
public class Dim3dIcon extends Dim3dResourceBaseEntity {
    /** 新增名称与当前表单一致，去空白及业务长度检查由图片服务再次执行。 */
    @NotBlank @Size(min = 1, max = 50) private String iconName;
    @Size(max = 500) private String iconDesc;
    @NotBlank private String category;
    private String secondaryCategory;
    @NotBlank @Pattern(regexp = "SYSTEM|MINE") private String resourceBelong = "SYSTEM";
    /** 对外使用字符串，避免浏览器解析大文件编号时失去精度。 */
    @NotNull @JsonSerialize(using = ToStringSerializer.class) private Long iconFileId;
    private String status = "ONLINE";

    /** 图片目录编号与普通文件编号均保持十进制字符串传输。 */
    @Override
    @JsonSerialize(using = ToStringSerializer.class)
    public Long getId() { return super.getId(); }
}
