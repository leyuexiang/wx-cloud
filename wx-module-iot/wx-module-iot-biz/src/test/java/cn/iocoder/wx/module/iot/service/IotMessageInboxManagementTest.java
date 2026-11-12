package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.IotMessageInboxController;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxPageReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 管理响应不能把底层投递异常中的连接信息或秘密带到浏览器。 */
class IotMessageInboxManagementTest {
    @Test
    void shouldRedactRawFailureAndNeverExposePayload() {
        var service = mock(IotMessageInboxService.class);
        var row = new IotMessageInboxDO();
        row.setId(1L);
        row.setStatus("FAILED");
        row.setErrorMessage("broker.invalid?password=private-secret");
        row.setPayloadJson("{\"token\":\"private-secret\"}");
        when(service.getPage(any())).thenReturn(new PageResult<>(List.of(row), 1L));
        var response = new IotMessageInboxController(service).page(new IotMessageInboxPageReqVO());
        var visible = response.getData().getList().get(0);
        assertNotNull(visible.getErrorMessage());
        assertFalse(visible.getErrorMessage().contains("private-secret"));
        assertFalse(visible.getErrorMessage().contains("broker.invalid"));
        assertEquals("FAILED", visible.getStatus());
    }
}
