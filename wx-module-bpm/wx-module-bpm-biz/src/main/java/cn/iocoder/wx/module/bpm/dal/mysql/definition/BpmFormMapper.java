package cn.iocoder.wx.module.bpm.dal.mysql.definition;


import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.wx.module.bpm.controller.admin.definition.vo.form.BpmFormPageReqVO;
import cn.iocoder.wx.module.bpm.dal.dataobject.definition.BpmFormDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态表单 Mapper
 *
 * @author 风里雾里
 */
@Mapper
public interface BpmFormMapper extends BaseMapperX<BpmFormDO> {

    default PageResult<BpmFormDO> selectPage(BpmFormPageReqVO reqVO) {
        return selectPage(reqVO, new QueryWrapperX<BpmFormDO>()
                .likeIfPresent("name", reqVO.getName())
                .orderByDesc("id"));
    }

}
