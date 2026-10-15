package cn.iocoder.jtyf.module.trade.controller.app.base.spu;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品 SPU 基础 Response VO
 *
 * @author 九天芸菲
 */
@Data
public class AppProductSpuBaseRespVO {

    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "商品 SPU 名字", requiredMode = Schema.RequiredMode.REQUIRED, example = "九天")
    private String name;

    @Schema(description = "商品主图地址", example = "https://www.iocoder.cn/xx.png")
    private String picUrl;

    @Schema(description = "商品分类编号", example = "1")
    private Long categoryId;

}
