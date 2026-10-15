package cn.iocoder.wx.module.erp.dal.mysql.product;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.erp.controller.admin.product.vo.product.ErpProductPageReqVO;
import cn.iocoder.wx.module.erp.dal.dataobject.product.ErpProductDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * ERP 产品 Mapper
 *
 * @author 九天芸菲
 */
@Mapper
public interface ErpProductMapper extends BaseMapperX<ErpProductDO> {

    default PageResult<ErpProductDO> selectPage(ErpProductPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ErpProductDO>()
                .likeIfPresent(ErpProductDO::getName, reqVO.getName())
                .eqIfPresent(ErpProductDO::getCategoryId, reqVO.getCategoryId())
                .betweenIfPresent(ErpProductDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ErpProductDO::getId));
    }

    default Long selectCountByCategoryId(Long categoryId) {
        return selectCount(ErpProductDO::getCategoryId, categoryId);
    }

    default Long selectCountByUnitId(Long unitId) {
        return selectCount(ErpProductDO::getUnitId, unitId);
    }

    default List<ErpProductDO> selectListByStatus(Integer status) {
        return selectList(ErpProductDO::getStatus, status);
    }

}