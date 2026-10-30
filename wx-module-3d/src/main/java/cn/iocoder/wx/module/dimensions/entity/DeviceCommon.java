package cn.iocoder.wx.module.dimensions.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@TableName("wx_deviceall")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "设备表共有的属性实体")
public class DeviceCommon implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "服务器编码")
    @JsonProperty("serverCode")
    private String serverCode;

    @Schema(description = "园区")
    @JsonProperty("areaName")
    private String areaName;

    @Schema(description = "楼栋")
    @JsonProperty("buildingName")
    private String buildingName;

    @Schema(description = "楼层")
    @JsonProperty("floorName")
    private String floorName;

    @Schema(description = "机房名称")
    @JsonProperty("zonesubname")
    private String zonesubname;

    @Schema(description = "机房编号")
    @JsonProperty("zonesubno")
    private String zonesubno;

    @Schema(description = "设备类型名称")
    @JsonProperty("categoryName")
    private String categoryName;

    @Schema(description = "设备类型编码")
    @JsonProperty("categoryNo")
    private String categoryNo;

    @Schema(description = "设备类型名称_3d用")
    @JsonProperty("deviceTypeName")
    private String deviceTypeName;

    @Schema(description = "设备类型编码_3d用")
    @JsonProperty("deviceTypeCode")
    private String deviceTypeCode;

    @Schema(description = "设备id")
    @JsonProperty("deviceId")
    private String deviceId;

    @Schema(description = "设备名称")
    @JsonProperty("deviceName")
    private String deviceName;

    @Schema(description = "空间坐标")
    @JsonProperty("position")
    private String position;

    @Schema(description = "旋转")
    @JsonProperty("roll")
    private String roll;

    @Schema(description = "机房电话号码")
    @JsonProperty("zoneTelephone")
    private String zoneTelephone;
}
