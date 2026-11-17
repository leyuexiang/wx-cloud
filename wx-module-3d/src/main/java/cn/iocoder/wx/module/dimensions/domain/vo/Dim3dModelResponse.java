package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 模型目录对外投影，字段与参考响应一致，独立于数据库归属及文件预览数据。 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "模型目录详情及分页记录")
public record Dim3dModelResponse(
        String id, String modelName, String modelDesc, String category, String resourceBelong,
        String isPaid, String priceYuan, String coverImageId, String modelFileId, String status,
        String createBy, String createByName, String createTime,
        String updateBy, String updateByName, String updateTime) {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 受管关联在投影边界转换为文件编号，金额沿用整数分计算后保留两位小数。 */
    public static Dim3dModelResponse from(Dim3dModel model, String creatorName, String updaterName) {
        Long fileId = model.getResourceId() == null ? model.getModelFileId() : model.getResourceId();
        var price = model.getPriceYuan();
        return new Dim3dModelResponse(identifier(model.getId()), model.getModelName(), model.getModelDesc(),
                model.getCategory(), model.getResourceBelong(), model.getIsPaid(),
                price == null ? null : price.toPlainString(), identifier(model.getCoverImageId()),
                identifier(fileId), model.getStatus(), identifier(model.getCreateBy()), creatorName,
                time(model.getCreateTime()), identifier(model.getUpdateBy()), updaterName, time(model.getUpdateTime()));
    }

    private static String identifier(Long value) { return value == null ? null : value.toString(); }

    /** 在本接口显式形成时间文本，避免项目全局时间戳序列化规则改变参考响应格式。 */
    private static String time(LocalDateTime value) { return value == null ? null : TIME_FORMAT.format(value); }
}
