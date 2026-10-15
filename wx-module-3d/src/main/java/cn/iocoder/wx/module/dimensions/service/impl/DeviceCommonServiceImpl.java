package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.module.dimensions.entity.DeviceCommon;
import cn.iocoder.wx.module.dimensions.mapper.DeviceCommonMapper;
import cn.iocoder.wx.module.dimensions.service.DeviceCommonService;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@DS("data")
public class DeviceCommonServiceImpl extends ServiceImpl<DeviceCommonMapper, DeviceCommon> implements DeviceCommonService {

    @Resource
    DeviceCommonMapper deviceCommonMapper;

    @Override
    public List<DeviceCommon> selectByTableName(String tableName) {
        return deviceCommonMapper.selectByTableName(tableName);
    }

    @Override
    public List<DeviceCommon> selectWhereBuilding(String tableName, String building) {
        return deviceCommonMapper.selectWhereBuilding(tableName, building);
    }

    @Override
    public List<DeviceCommon> selectWhereFloor(String tableName, String building, String floor) {
        return deviceCommonMapper.selectWhereFloor(tableName, building, floor);
    }

    @Override
    public List<DeviceCommon> selectWhereSpaceName(String tableName, String building, String floor, String SpaceName) {
        return deviceCommonMapper.selectWhereSpaceName(tableName, building, floor, SpaceName);
    }

}
