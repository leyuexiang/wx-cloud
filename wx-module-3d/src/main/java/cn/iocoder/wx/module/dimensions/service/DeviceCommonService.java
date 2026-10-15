package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.entity.DeviceCommon;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DeviceCommonService extends IService<DeviceCommon> {

    // 动态设置表名
    List<DeviceCommon> selectByTableName(String tableName);

    List<DeviceCommon> selectWhereBuilding(String tableName, String building);

    List<DeviceCommon> selectWhereFloor(String tableName, String building, String floor);

    List<DeviceCommon> selectWhereSpaceName(String tableName, String building, String floor, String SpaceName);


}
