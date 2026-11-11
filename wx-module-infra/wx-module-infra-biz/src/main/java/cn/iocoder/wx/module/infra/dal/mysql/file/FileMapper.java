package cn.iocoder.wx.module.infra.dal.mysql.file;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.infra.controller.admin.file.vo.file.FilePageReqVO;
import cn.iocoder.wx.module.infra.dal.dataobject.file.FileDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件操作 Mapper
 *
 * @author 九天芸菲
 */
@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    default PageResult<FileDO> selectPage(FilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FileDO>()
                .likeIfPresent(FileDO::getPath, reqVO.getPath())
                .likeIfPresent(FileDO::getType, reqVO.getType())
                .betweenIfPresent(FileDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FileDO::getId));
    }

    /**
     * 本表处于多租户忽略清单，故不依赖拦截器；每次按编号读取都显式检查数据库中的归属状态。
     * LEGACY/null 一律不可见，PRIVATE_UPLOAD 仅上传者可读，TENANT 限同租户，GLOBAL 必须为哨兵租户 0。
     * tenant_id 列本身须在目标库确认存在；本 SQL 不会把旧记录提升为可见对象。
     */
    @Select("SELECT * FROM wx_infra_file WHERE id = #{id} AND deleted = 0 AND ("
            + "(visibility_scope = 'PRIVATE_UPLOAD' AND tenant_id = #{tenantId} AND uploader_id = #{userId}) "
            + "OR (visibility_scope = 'TENANT' AND tenant_id = #{tenantId}) "
            + "OR (visibility_scope = 'GLOBAL' AND tenant_id = 0)) LIMIT 1")
    FileDO selectVisibleById(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("userId") Long userId);

}
