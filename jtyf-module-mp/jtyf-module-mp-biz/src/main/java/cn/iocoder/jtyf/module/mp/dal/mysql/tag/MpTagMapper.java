package cn.iocoder.jtyf.module.mp.dal.mysql.tag;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.jtyf.module.mp.controller.admin.tag.vo.MpTagPageReqVO;
import cn.iocoder.jtyf.module.mp.dal.dataobject.tag.MpTagDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MpTagMapper extends BaseMapperX<MpTagDO> {

    default PageResult<MpTagDO> selectPage(MpTagPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MpTagDO>()
                .eqIfPresent(MpTagDO::getAccountId, reqVO.getAccountId())
                .likeIfPresent(MpTagDO::getName, reqVO.getName())
                .orderByDesc(MpTagDO::getId));
    }

    default List<MpTagDO> selectListByAccountId(Long accountId) {
        return selectList(MpTagDO::getAccountId, accountId);
    }

}
