package cn.iocoder.wx.module.dimensions.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.wx.module.dimensions.entity.DeviceCommon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceCommonMapper extends BaseMapper<DeviceCommon> {

    // 动态设置表名
    List<DeviceCommon> selectByTableName(@Param("tableName") String tableName);

    List<DeviceCommon> selectWhereArea(@Param("area") String area);

    List<DeviceCommon> selectWhereBuilding(@Param("area") String area, @Param("building") String building);

    List<DeviceCommon> selectWhereFloor(@Param("area") String area, @Param("building") String building, @Param("floor") String floor);

    List<DeviceCommon> selectWhereSpaceName(@Param("area") String area, @Param("building") String building, @Param("floor") String floor, @Param("SpaceName") String SpaceName);

    List<DeviceCommon> selectWhereDeviceTypeCode(@Param("area") String area, @Param("building") String building,
                                                 @Param("floor") String floor, @Param("SpaceName") String SpaceName, @Param("deviceTypeCode") String deviceTypeCode);
    List<String> selectDeviceTypeNameList();

    List<DeviceCommon> selectDeviceAllList();

    List<String> selectDeviceCode(@Param("deviceTypeName") String deviceTypeName,@Param("deviceTypeCode") String deviceTypeCode);


}
