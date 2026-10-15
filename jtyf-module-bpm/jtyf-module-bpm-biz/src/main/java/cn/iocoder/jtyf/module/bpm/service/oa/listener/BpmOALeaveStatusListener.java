package cn.iocoder.jtyf.module.bpm.service.oa.listener;

import cn.iocoder.jtyf.module.bpm.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.jtyf.module.bpm.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.jtyf.module.bpm.service.oa.BpmOALeaveService;
import cn.iocoder.jtyf.module.bpm.service.oa.BpmOALeaveServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * OA 请假单的结果的监听器实现类
 *
 * @author 九天芸菲
 */
@Component
public class BpmOALeaveStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private BpmOALeaveService leaveService;

    @Override
    protected String getProcessDefinitionKey() {
        return BpmOALeaveServiceImpl.PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        leaveService.updateLeaveStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }

}
