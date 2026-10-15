package cn.iocoder.wx.module.erp.dal.mysql.sale;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.erp.controller.admin.sale.vo.customer.ErpCustomerPageReqVO;
import cn.iocoder.wx.module.erp.dal.dataobject.sale.ErpCustomerDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ERP 客户 Mapper
 *
 * @author 九天芸菲
 */
@Mapper
public interface ErpCustomerMapper extends BaseMapperX<ErpCustomerDO> {

    default PageResult<ErpCustomerDO> selectPage(ErpCustomerPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpCustomerDO>()
                .likeIfPresent(ErpCustomerDO::getName, reqVO.getName())
                .eqIfPresent(ErpCustomerDO::getMobile, reqVO.getMobile())
                .eqIfPresent(ErpCustomerDO::getTelephone, reqVO.getTelephone())
                .orderByDesc(ErpCustomerDO::getId));
    }

    default List<ErpCustomerDO> selectListByStatus(Integer status) {
        return selectList(ErpCustomerDO::getStatus, status);
    }

}