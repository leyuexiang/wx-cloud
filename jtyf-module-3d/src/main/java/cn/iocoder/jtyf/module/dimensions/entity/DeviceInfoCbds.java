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
    @Schema(description = "组合传感器设备表信息")
    public class DeviceInfoCbds implements Serializable {
        private static final long serialVersionUID = 1L;

        @TableId(value = "id", type = IdType.AUTO)
        private Integer id;

        @Schema(description = "设备id")
        private String deviceId;

        @Schema(description = "设备名字")
        private String deviceName;

        @Schema(description = "区域Id")
        private String areaId;

        @Schema(description = "区域名字")
        private String areaName;

        @Schema(description = "栋数Id")
        private String buildingId;

        @Schema(description = "栋数名称")
        private String buildingName;

        @Schema(description = "楼层Id")
        private String floorId;

        @Schema(description = "楼层名称")
        private String floorName;

        @Schema(description = "地点Id")
        private String spaceId;

        @Schema(description = "地点名称")
        private String spaceName;

        @Schema(description = "设备类型")
        private String type;

        @Schema(description = "设备类型名称")
        private String typeName;

        @Schema(description = "lon")
        private Double lon;

        @Schema(description = "lat")
        private Double lat;


}
