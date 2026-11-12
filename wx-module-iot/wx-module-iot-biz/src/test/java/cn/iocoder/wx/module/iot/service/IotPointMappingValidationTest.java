package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 保存时拒绝无法执行的映射，避免首次设备上报才暴露配置错误。 */
class IotPointMappingValidationTest {
    private final IotPointMappingService service = new IotPointMappingService(new ObjectMapper());

    @Test
    void shouldAcceptOptionalAndValidMappings() {
        for (String json : new String[]{null, "", "{}", "{\"points\":{\"temp\":{\"valueType\":\"number\",\"scale\":0.1,\"offset\":-2}}}"}) {
            assertDoesNotThrow(() -> service.validateConfiguration(json));
        }
    }

    @Test
    void shouldRejectMalformedStructureAndInvalidConversionParameters() {
        for (String json : new String[]{"{", "[]", "null", "{\"points\":[]}",
                "{\"points\":{\"a\":true}}", "{\"points\":{\"a\":{\"scale\":\"1\"}}}",
                "{\"points\":{\"a\":{\"offset\":false}}}", "{\"points\":{\"a\":{\"valueType\":\"UNKNOWN\"}}}",
                "{\"points\":{\"a\":{\"pointCode\":[]}}}"}) {
            assertThrows(ServiceException.class, () -> service.validateConfiguration(json), json);
        }
    }
}
