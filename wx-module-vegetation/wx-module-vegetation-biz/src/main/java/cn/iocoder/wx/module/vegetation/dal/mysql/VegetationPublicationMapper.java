package cn.iocoder.wx.module.vegetation.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.module.vegetation.dal.dataobject.VegetationPublicationDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 发布版本数据访问接口。 */
@Mapper
public interface VegetationPublicationMapper extends BaseMapperX<VegetationPublicationDO> {

    /** 并发发布前确保场景锁行存在；INSERT IGNORE把首次创建竞争交给数据库主键处理。 */
    @Insert("INSERT IGNORE INTO wx_veg_publication_lock (scene_id) VALUES (#{sceneId})")
    int ensureSceneLock(String sceneId);

    /** 在当前事务中锁定场景行；锁会持续到发布或回滚事务提交。 */
    @Select("SELECT scene_id FROM wx_veg_publication_lock WHERE scene_id = #{sceneId} FOR UPDATE")
    String lockScene(String sceneId);

    default VegetationPublicationDO selectBySceneIdAndVersion(String sceneId, String publicationVersion) {
        return selectOne(VegetationPublicationDO::getSceneId, sceneId,
                VegetationPublicationDO::getPublicationVersion, publicationVersion);
    }

    default VegetationPublicationDO selectCurrentBySceneId(String sceneId) {
        return selectOne(new LambdaQueryWrapper<VegetationPublicationDO>()
                .eq(VegetationPublicationDO::getSceneId, sceneId)
                .eq(VegetationPublicationDO::getStatus, "STABLE")
                .orderByDesc(VegetationPublicationDO::getUpdateTime)
                .last("LIMIT 1"));
    }

    default List<VegetationPublicationDO> selectBySceneId(String sceneId) {
        return selectList(new LambdaQueryWrapper<VegetationPublicationDO>()
                .eq(VegetationPublicationDO::getSceneId, sceneId)
                .orderByDesc(VegetationPublicationDO::getCreateTime));
    }

    default int markScenePublicationsRolledBack(String sceneId) {
        return update(null, new LambdaUpdateWrapper<VegetationPublicationDO>()
                .eq(VegetationPublicationDO::getSceneId, sceneId)
                .eq(VegetationPublicationDO::getStatus, "STABLE")
                .set(VegetationPublicationDO::getStatus, "ROLLED_BACK"));
    }
}
