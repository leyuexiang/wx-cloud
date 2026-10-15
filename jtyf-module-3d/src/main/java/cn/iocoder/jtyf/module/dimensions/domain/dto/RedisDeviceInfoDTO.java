package cn.iocoder.jtyf.module.dimensions.domain.dto;

import lombok.Data;

/**
 * @author: cheYu
 * @desc: 存储在redis的设备信息
 * @create: 2025-06-17 15:30
 */
@Data
public class RedisDeviceInfoDTO {
    private String status;

    private String data;
}
