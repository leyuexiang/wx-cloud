package cn.iocoder.jtyf.module.system.api.logger;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.common.util.object.BeanUtils;
import cn.iocoder.jtyf.module.system.api.logger.dto.OperateLogCreateReqDTO;
import cn.iocoder.jtyf.module.system.api.logger.dto.OperateLogPageReqDTO;
import cn.iocoder.jtyf.module.system.api.logger.dto.OperateLogRespDTO;
import cn.iocoder.jtyf.module.system.dal.dataobject.logger.OperateLogDO;
import cn.iocoder.jtyf.module.system.service.logger.OperateLogService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.jtyf.framework.common.pojo.CommonResult.success;

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
