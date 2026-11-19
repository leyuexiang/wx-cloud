package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.enums.GlobalErrorCodeConstants;

import java.util.List;

/** 文件型及合并分页使用同一根级响应，合并记录继续携带目录来源。 */
public record Dim3dTemplatePageResponse<T>(Integer code, String msg, Long total, List<T> rows) {
    public Dim3dTemplatePageResponse(Long total, List<T> rows) {
        this(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), total, rows);
    }
}
