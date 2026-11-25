package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.enums.GlobalErrorCodeConstants;

import java.util.List;

/** 图标分页按目录契约在根级返回总数及记录，详情和分页共用业务投影。 */
public record Dim3dIconPageResponse(Integer code, String msg, Long total, List<Dim3dIconResponse> rows) {
    public Dim3dIconPageResponse(Long total, List<Dim3dIconResponse> rows) {
        this(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), total, rows);
    }
}
