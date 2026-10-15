package cn.iocoder.jtyf.module.ai.dal.mysql.model;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.jtyf.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.jtyf.module.ai.controller.admin.model.vo.apikey.AiApiKeyPageReqVO;
import cn.iocoder.jtyf.module.ai.dal.dataobject.model.AiApiKeyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI API 密钥 Mapper
 *
 * @author 九天芸菲
 */
@Mapper
public interface AiApiKeyMapper extends BaseMapperX<AiApiKeyDO> {

    default PageResult<AiApiKeyDO> selectPage(AiApiKeyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AiApiKeyDO>()
                .likeIfPresent(AiApiKeyDO::getName, reqVO.getName())
                .eqIfPresent(AiApiKeyDO::getPlatform, reqVO.getPlatform())
                .eqIfPresent(AiApiKeyDO::getStatus, reqVO.getStatus())
                .orderByDesc(AiApiKeyDO::getId));
    }

    default AiApiKeyDO selectFirstByPlatformAndStatus(String platform, Integer status) {
        return selectOne(new QueryWrapperX<AiApiKeyDO>()
                .eq("platform", platform)
                .eq("status", status)
                .limitN(1)
                .orderByAsc("id"));
    }

}