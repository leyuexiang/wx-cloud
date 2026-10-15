package cn.iocoder.jtyf.module.crm.dal.mysql.business;


import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.module.crm.dal.dataobject.business.CrmBusinessProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 商机产品 Mapper
 *
 * @author lzxhqs
 */
@Mapper
public interface CrmBusinessProductMapper extends BaseMapperX<CrmBusinessProductDO> {

    default List<CrmBusinessProductDO> selectListByBusinessId(Long businessId) {
        return selectList(CrmBusinessProductDO::getBusinessId, businessId);
    }

}
