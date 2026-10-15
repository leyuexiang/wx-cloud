package cn.iocoder.wx.module.bpm.framework.flowable.core.event;

import cn.iocoder.wx.module.bpm.event.BpmProcessInstanceStatusEvent;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;

/**
 * {@link BpmProcessInstanceStatusEvent} 的生产者
 *
 * @author 九天芸菲
 */
@AllArgsConstructor
@Validated
public class BpmProcessInstanceEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void sendProcessInstanceResultEvent(@Valid BpmProcessInstanceStatusEvent event) {
        publisher.publishEvent(event);
    }

}
