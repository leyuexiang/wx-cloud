package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 图标详情与列表共用业务字段；普通图片以文件编号通过原授权入口预览。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "图标目录详情及分页记录")
public record Dim3dIconResponse(
        String id, String iconName, String iconDesc, String category, String secondaryCategory,
        String resourceBelong, String iconFileId, String status, String createBy, String createByName,
        String createTime, String updateBy, String updateByName, String updateTime) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 明确转换编号及审计时间，保留历史空值和超过浏览器安全整数范围的编号。 */
    public static Dim3dIconResponse from(Dim3dIcon icon, String creatorName, String updaterName) {
        return new Dim3dIconResponse(identifier(icon.getId()), icon.getIconName(), icon.getIconDesc(),
                icon.getCategory(), icon.getSecondaryCategory(), icon.getResourceBelong(),
                identifier(icon.getIconFileId()), icon.getStatus(), identifier(icon.getCreateBy()), creatorName,
                time(icon.getCreateTime()), identifier(icon.getUpdateBy()), updaterName, time(icon.getUpdateTime()));
    }

    private static String identifier(Long value) { return value == null ? null : value.toString(); }
    private static String time(LocalDateTime value) { return value == null ? null : TIME_FORMAT.format(value); }
}
