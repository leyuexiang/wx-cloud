package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.wx.framework.common.pojo.PageResult;

import java.util.List;

/**
 * 模型分页严格使用根级 code、msg、total、rows，记录使用与详情相同的响应投影。
 * 不增加第二层分页包装，避免同一批目录重复编码和传输。
 */
public record Dim3dModelPageResponse(Integer code, String msg, Long total, List<Dim3dModelResponse> rows) {
    public Dim3dModelPageResponse(PageResult<Dim3dModelResponse> page) {
        this(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(),
                page.getTotal(), page.getList());
    }
}
