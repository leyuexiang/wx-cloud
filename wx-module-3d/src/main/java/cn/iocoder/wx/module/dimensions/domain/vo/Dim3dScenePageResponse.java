package cn.iocoder.wx.module.dimensions.domain.vo;

import cn.iocoder.wx.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.wx.framework.common.pojo.PageResult;

import java.util.List;
import java.util.Map;

/** 场景管理分页按根级返回总数与业务记录。 */
public record Dim3dScenePageResponse(Integer code, String msg, Long total, List<Map<String, Object>> rows) {
    public Dim3dScenePageResponse(PageResult<Map<String, Object>> page) {
        this(GlobalErrorCodeConstants.SUCCESS.getCode(), GlobalErrorCodeConstants.SUCCESS.getMsg(), page.getTotal(), page.getList());
    }
}
