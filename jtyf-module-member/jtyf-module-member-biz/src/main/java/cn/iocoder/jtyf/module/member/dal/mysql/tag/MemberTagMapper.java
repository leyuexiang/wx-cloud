package cn.iocoder.jtyf.module.member.dal.mysql.tag;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.jtyf.module.member.controller.admin.tag.vo.MemberTagPageReqVO;
import cn.iocoder.jtyf.module.member.dal.dataobject.tag.MemberTagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员标签 Mapper
 *
 * @author 九天芸菲
 */
@Mapper
public interface MemberTagMapper extends BaseMapperX<MemberTagDO> {

    default PageResult<MemberTagDO> selectPage(MemberTagPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberTagDO>()
                .likeIfPresent(MemberTagDO::getName, reqVO.getName())
                .betweenIfPresent(MemberTagDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MemberTagDO::getId));
    }

    default MemberTagDO selelctByName(String name) {
        return selectOne(MemberTagDO::getName, name);
    }
}
