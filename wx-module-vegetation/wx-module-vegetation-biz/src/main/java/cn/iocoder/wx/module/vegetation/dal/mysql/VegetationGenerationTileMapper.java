package cn.iocoder.wx.module.vegetation.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.module.vegetation.dal.dataobject.VegetationGenerationTileDO;
import org.apache.ibatis.annotations.Mapper;

/** 地块结果数据访问接口；任务号与地块号组合唯一，重复回调不会产生重复地块。 */
@Mapper
public interface VegetationGenerationTileMapper extends BaseMapperX<VegetationGenerationTileDO> {

    default VegetationGenerationTileDO selectByTaskIdAndTileId(Long taskId, String tileId) {
        return selectOne(VegetationGenerationTileDO::getTaskId, taskId,
                VegetationGenerationTileDO::getTileId, tileId);
    }
}
