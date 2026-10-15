package cn.iocoder.jtyf.module.vegetation.dal.mysql;

import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.module.vegetation.dal.dataobject.VegetationGenerationTaskDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/** 生成任务数据访问接口；幂等键和未完成状态查询均走数据库索引。 */
@Mapper
public interface VegetationGenerationTaskMapper extends BaseMapperX<VegetationGenerationTaskDO> {

    default VegetationGenerationTaskDO selectByIdempotencyKey(String idempotencyKey) {
        return selectOne(VegetationGenerationTaskDO::getIdempotencyKey, idempotencyKey);
    }

    default VegetationGenerationTaskDO selectByGeneratorTaskId(String generatorTaskId) {
        return selectOne(VegetationGenerationTaskDO::getGeneratorTaskId, generatorTaskId);
    }

    default List<VegetationGenerationTaskDO> selectUnfinishedTasks(Collection<String> statuses) {
        return selectList(new LambdaQueryWrapper<VegetationGenerationTaskDO>()
                .in(VegetationGenerationTaskDO::getStatus, statuses)
                .orderByAsc(VegetationGenerationTaskDO::getCreateTime));
    }
}
