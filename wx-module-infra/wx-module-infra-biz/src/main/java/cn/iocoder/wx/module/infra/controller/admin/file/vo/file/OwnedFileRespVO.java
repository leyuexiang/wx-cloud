package cn.iocoder.wx.module.infra.controller.admin.file.vo.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 图片文件上传与按编号解析响应；签名地址为临时值，不能持久化到业务目录。 */
@Data
@Schema(description = "图片文件临时访问信息")
public class OwnedFileRespVO {

    @Schema(description = "本地基础设施文件编号，按十进制字符串传输", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ossId;

    @Schema(description = "不超过十分钟的临时读取 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "已清理路径片段的原文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalName;

    @Schema(description = "小写文件后缀，不含点", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileSuffix;
}
