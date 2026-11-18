package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增与编辑共用业务表单，目录身份、状态和审计字段由服务端决定。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "图标新增及编辑表单")
public record Dim3dIconForm(
        @NotBlank @Size(max = 50) String iconName,
        @Size(max = 500) String iconDesc,
        @NotBlank @Size(max = 32) String category,
        @Size(max = 32) String secondaryCategory,
        @NotBlank @Pattern(regexp = "SYSTEM|MINE") String resourceBelong,
        @NotBlank @Pattern(regexp = "[0-9]{1,19}") String iconFileId) {

    /** 按十进制精确解析文件编号，避免数字转换截断，文件内容及可绑定范围由业务服务校验。 */
    public Dim3dIcon toIcon() {
        long fileId;
        try {
            fileId = Long.parseLong(iconFileId);
        } catch (NumberFormatException invalid) {
            throw new ServiceException(400, "图片文件编号无效");
        }
        if (fileId <= 0) throw new ServiceException(400, "图片文件编号无效");
        var icon = new Dim3dIcon();
        icon.setIconName(iconName);
        icon.setIconDesc(iconDesc);
        icon.setCategory(category);
        icon.setSecondaryCategory(secondaryCategory);
        icon.setResourceBelong(resourceBelong);
        icon.setIconFileId(fileId);
        return icon;
    }
}
