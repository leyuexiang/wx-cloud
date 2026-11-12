package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;

import static cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.POINT_MAPPING_INVALID;

/** 根据设备绑定中的 JSON 配置完成点位编码、类型、单位和数值换算。 */
@Service
public class IotPointMappingService {

    private final ObjectReader mappingReader;

    /** 复用不可变读取器，不修改全局映射器，避免高频换算重复创建或提前损失精度。 */
    public IotPointMappingService(ObjectMapper objectMapper) {
        this.mappingReader = objectMapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** 校验配置结构及全部点位参数；空配置保留原样透传的历史行为。 */
    public void validateConfiguration(String json) {
        if (json == null || json.isBlank()) return;
        if (json.length() > 65536) throw exception(POINT_MAPPING_INVALID);
        try {
            JsonNode root = mappingReader.readTree(json);
            if (root == null || !root.isObject()) throw exception(POINT_MAPPING_INVALID);
            JsonNode points = root.get("points");
            if (points == null || points.isNull()) return;
            if (!points.isObject()) throw exception(POINT_MAPPING_INVALID);
            var entries = points.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                JsonNode mapping = entry.getValue();
                if (entry.getKey().isBlank() || !mapping.isObject()) throw exception(POINT_MAPPING_INVALID);
                for (String field : new String[]{"pointCode", "valueType", "targetUnit"}) {
                    JsonNode value = mapping.get(field);
                    if (value != null && !value.isNull() && (!value.isTextual()
                            || ("valueType".equals(field) && value.asText().isBlank()))) {
                        throw exception(POINT_MAPPING_INVALID);
                    }
                }
                String type = textOrDefault(mapping, "valueType", null);
                if (type != null) IotValueType.valueOf(type.toUpperCase(Locale.ROOT));
                decimalOrDefault(mapping, "scale", BigDecimal.ONE);
                decimalOrDefault(mapping, "offset", BigDecimal.ZERO);
            }
        } catch (Exception ex) {
            // 错误信息固定，不回显用户提交的完整映射或内部解析异常。
            throw exception(POINT_MAPPING_INVALID);
        }
    }

    public IotMappedPoint map(IotDeviceBindingDO binding, String pointCode,
                              Object value, IotValueType valueType, String unit) {
        if (binding == null || binding.getPointMappingJson() == null
                || binding.getPointMappingJson().isBlank()) {
            return new IotMappedPoint(pointCode, value, valueType, unit);
        }
        try {
            JsonNode root = mappingReader.readTree(binding.getPointMappingJson());
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
