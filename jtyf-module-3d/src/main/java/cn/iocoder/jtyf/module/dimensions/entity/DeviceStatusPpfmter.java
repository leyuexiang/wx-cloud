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
@Schema(description = "人流量统计仪状态信息")
public class DeviceStatusPpfmter implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "设备id")
    private String deviceId;

    @Schema(description = "设备名字")
    private String deviceName;

    @Schema(description = "在线数")
    private Integer participants;

    @Schema(description = "离线数")
    private Integer departures;

    @Schema(description = "数量")
    private Integer headCount;

    @Schema(description = "状态")
    private Integer  state;

    @Schema(description = "状态名")
    private String  stateName;

    @Schema(description = "是否预警")
    private Integer  warning;

}
