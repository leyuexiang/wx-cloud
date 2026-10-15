package cn.iocoder.wx.module.system.api.logger;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.system.api.logger.dto.OperateLogCreateReqDTO;
import cn.iocoder.wx.module.system.api.logger.dto.OperateLogPageReqDTO;
import cn.iocoder.wx.module.system.api.logger.dto.OperateLogRespDTO;
import cn.iocoder.wx.module.system.dal.dataobject.logger.OperateLogDO;
import cn.iocoder.wx.module.system.service.logger.OperateLogService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class OperateLogApiImpl implements OperateLogApi {

    @Resource
    private OperateLogService operateLogService;

    @Override
    public CommonResult<Boolean> createOperateLog(OperateLogCreateReqDTO createReqDTO) {
        operateLogService.createOperateLog(createReqDTO);
        return success(true);
    }

    @Override
    public CommonResult<PageResult<OperateLogRespDTO>> getOperateLogPage(OperateLogPageReqDTO pageReqDTO) {
        PageResult<OperateLogDO> operateLogPage = operateLogService.getOperateLogPage(pageReqDTO);
        return success(BeanUtils.toBean(operateLogPage, OperateLogRespDTO.class));
    }

}
