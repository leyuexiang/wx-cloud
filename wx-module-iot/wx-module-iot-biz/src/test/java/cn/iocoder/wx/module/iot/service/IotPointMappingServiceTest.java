package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.POINT_MAPPING_INVALID;
import static org.junit.jupiter.api.Assertions.*;

/** 点位换算回归测试：保护精度、默认参数及非法配置的显式失败行为。 */
class IotPointMappingServiceTest {

    private final IotPointMappingService service = new IotPointMappingService(new ObjectMapper());

    /** 未绑定映射时原样保留遥测，避免影响原有标准报文入口。 */
    @Test
    void shouldPassThroughWithoutBinding() {
        assertEquals(new IotMappedPoint("temperature", 10, IotValueType.NUMBER, "C"),
                service.map(null, "temperature", 10, IotValueType.NUMBER, "C"));
    }

    /** 使用十进制乘加，验证小数换算不会引入浮点误差。 */
    @Test
    void shouldApplyScaleAndOffset() {
        IotMappedPoint result = map("\"scale\":0.1,\"offset\":0.2");
        assertEquals(0, new BigDecimal("1.2").compareTo((BigDecimal) result.value()));
    }

    /** 缺省参数对应恒等变换，不能将缺省比例读成零。 */
    @Test
    void shouldDefaultToIdentityConversion() {
        assertEquals(0, BigDecimal.TEN.compareTo((BigDecimal) map("\"pointCode\":\"temperature\"").value()));
    }

    /** 保留既有 null 默认语义，兼容历史映射配置。 */
    @Test
    void shouldDefaultNullParameters() {
        assertEquals(0, BigDecimal.TEN.compareTo((BigDecimal) map("\"scale\":null,\"offset\":null").value()));
    }

    /** 零与负数是合法换算参数，不应被当成缺省值或非法值。 */
    @Test
    void shouldAllowZeroScaleAndNegativeOffset() {
        assertEquals(0, new BigDecimal("-2").compareTo((BigDecimal) map("\"scale\":0,\"offset\":-2").value()));
    }

    /** 非数值配置必须拒绝，防止 Jackson 将字符串、布尔值或容器静默转换成零。 */
    @ParameterizedTest
    @ValueSource(strings = {"\"scale\":\"bad\"", "\"scale\":\"0.1\"", "\"scale\":true",
            "\"offset\":false", "\"offset\":{}", "\"offset\":[]"})
    void shouldRejectNonNumericConversionParameters(String fields) {
        ServiceException error = assertThrows(ServiceException.class, () -> map(fields));
        assertEquals(POINT_MAPPING_INVALID.getCode(), error.getCode());
    }

    /** 每次使用独立设备绑定，避免测试之间共享可变映射配置。 */
    private IotMappedPoint map(String fields) {
        IotDeviceBindingDO binding = new IotDeviceBindingDO();
        binding.setPointMappingJson("{\"points\":{\"temperature\":{" + fields + "}}}");
        return service.map(binding, "temperature", 10, IotValueType.NUMBER, "C");
    }
}
