package cn.iocoder.jtyf.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "组合传感器状态信息")
public class DeviceStatusCbds implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "设备id")
    private String deviceId;

    @Schema(description = "设备名字")
    private String deviceName;

    @Schema(description = "温度")
    private Integer temperature;

    @Schema(description = "湿度")
    private Double humidity;

    @Schema(description = "co")
    private Double co;

    @Schema(description = "co2")
    private Double co2;

    @Schema(description = "状态码")
    private Integer  state;

    @Schema(description = "状态名")
    private String  stateName;

    @Schema(description = "是否预警")
    private Integer  warning;

}
