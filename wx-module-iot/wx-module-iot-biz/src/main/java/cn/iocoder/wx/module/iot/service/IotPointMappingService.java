package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.POINT_MAPPING_INVALID;

/** 根据设备绑定中的 JSON 配置完成点位编码、类型、单位和数值换算。 */
@Service
@RequiredArgsConstructor
public class IotPointMappingService {

    private final ObjectMapper objectMapper;

    public IotMappedPoint map(IotDeviceBindingDO binding, String pointCode,
                              Object value, IotValueType valueType, String unit) {
        if (binding == null || binding.getPointMappingJson() == null
                || binding.getPointMappingJson().isBlank()) {
            return new IotMappedPoint(pointCode, value, valueType, unit);
        }
        try {
            JsonNode root = objectMapper.readTree(binding.getPointMappingJson());
            JsonNode mapping = root.path("points").path(pointCode);
            if (mapping.isMissingNode() || mapping.isNull()) {
                return new IotMappedPoint(pointCode, value, valueType, unit);
            }
            String mappedPointCode = textOrDefault(mapping, "pointCode", pointCode);
            IotValueType mappedType = valueType;
            String configuredType = textOrDefault(mapping, "valueType", null);
            if (configuredType != null) {
                mappedType = IotValueType.valueOf(configuredType.toUpperCase(Locale.ROOT));
            }
            String mappedUnit = textOrDefault(mapping, "targetUnit", unit);
            Object mappedValue = convertValue(value, mappedType, mapping);
            return new IotMappedPoint(mappedPointCode, mappedValue, mappedType, mappedUnit);
        } catch (Exception ex) {
            throw exception(POINT_MAPPING_INVALID);
        }
    }

    private Object convertValue(Object value, IotValueType valueType, JsonNode mapping) {
        if (valueType != IotValueType.NUMBER) {
            return value;
        }
        BigDecimal number = value instanceof Number
                ? new BigDecimal(value.toString())
                : new BigDecimal(String.valueOf(value));
        BigDecimal scale = decimalOrDefault(mapping, "scale", BigDecimal.ONE);
        BigDecimal offset = decimalOrDefault(mapping, "offset", BigDecimal.ZERO);
        return number.multiply(scale).add(offset).stripTrailingZeros();
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private BigDecimal decimalOrDefault(JsonNode node, String field, BigDecimal defaultValue) {
        JsonNode value = node.get(field);
        // 缺失或显式 null 沿用历史默认值；零与负数仍是有效换算参数。
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        // 非数字节点的 decimalValue() 可能静默返回零，必须先验证类型，避免污染遥测数据。
        // 不接受数字字符串，确保映射配置中的 scale/offset 始终遵循 JSON 数字契约。
        if (!value.isNumber()) {
            throw exception(POINT_MAPPING_INVALID);
        }
        return value.decimalValue();
    }
}
