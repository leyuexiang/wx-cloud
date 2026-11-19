package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** 文件型模板目录的业务投影，目录编号、封面编号及模板文件编号各自保全。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "文件型模板详情及分页记录")
public record Dim3dTemplateResponse(
        String id, String templateName, String templateDesc, String templateType, String category,
        String resourceBelong, String coverImageId, String templateFileId, String status,
        String createBy, String createByName, String createTime, String updateBy, String updateByName, String updateTime) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 目录和审计编号按十进制字符串传输，历史缺值继续保留。 */
    public static Dim3dTemplateResponse from(Dim3dTemplate template, String creatorName, String updaterName) {
        return new Dim3dTemplateResponse(Objects.toString(template.getId(), null), template.getTemplateName(),
                template.getTemplateDesc(), template.getTemplateType(), template.getCategory(), template.getResourceBelong(),
                Objects.toString(template.getCoverImageId(), null), Objects.toString(template.getTemplateFileId(), null),
                template.getStatus(), Objects.toString(template.getCreateBy(), null), creatorName, time(template.getCreateTime()),
                Objects.toString(template.getUpdateBy(), null), updaterName, time(template.getUpdateTime()));
    }

    private static String time(LocalDateTime value) { return value == null ? null : TIME_FORMAT.format(value); }
}
