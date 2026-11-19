package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 文件型模板写入表单；状态、租户及审计信息由服务端维护。 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "文件型模板新增及编辑表单")
public record Dim3dTemplateForm(
        @NotBlank @Size(min = 2, max = 50) String templateName,
        @Size(max = 500) String templateDesc,
        @NotBlank @Pattern(regexp = "INDUSTRY|EXHIBITION|dim3d_template_industry|dim3d_template_exhibition") String templateType,
        @NotBlank @Size(max = 32) String category,
        @NotBlank @Pattern(regexp = "SYSTEM|MINE") String resourceBelong,
        @NotBlank @Pattern(regexp = "[0-9]{1,19}") String templateFileId,
        @Pattern(regexp = "[0-9]{1,19}") String coverImageId) {

    /** 精确解析十进制文件编号，避免浮点截断或溢出。封面可省略。 */
    public Dim3dTemplate toTemplate() {
        var template = new Dim3dTemplate();
        String name = templateName.trim();
        if (name.length() < 2) throw new ServiceException(400, "模板名称至少需要两个字符");
        template.setTemplateName(name);
        template.setTemplateDesc(templateDesc);
        template.setTemplateType(templateType);
        template.setCategory(category);
        template.setResourceBelong(resourceBelong);
        template.setTemplateFileId(identifier(templateFileId));
        template.setCoverImageId(coverImageId == null ? null : identifier(coverImageId));
        return template;
    }

    private static long identifier(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException invalid) {
            throw new ServiceException(400, "模板文件或封面编号无效");
        }
        throw new ServiceException(400, "模板文件或封面编号无效");
    }
}
