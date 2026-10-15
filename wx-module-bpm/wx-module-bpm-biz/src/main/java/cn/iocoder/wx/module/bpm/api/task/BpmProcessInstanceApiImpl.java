package cn.iocoder.wx.module.bpm.api.task;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.wx.module.bpm.service.task.BpmProcessInstanceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/**
 * Flowable 流程实例 Api 实现类
 *
 * @author 九天芸菲
 * @author jason
 */
@RestController
@Validated
public class BpmProcessInstanceApiImpl implements BpmProcessInstanceApi {

    @Resource
    private BpmProcessInstanceService processInstanceService;

    @Override
    public CommonResult<String> createProcessInstance(Long userId, @Valid BpmProcessInstanceCreateReqDTO reqDTO) {
        return success(processInstanceService.createProcessInstance(userId, reqDTO));
    }

}
