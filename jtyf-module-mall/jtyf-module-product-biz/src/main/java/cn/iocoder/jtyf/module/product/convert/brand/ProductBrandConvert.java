package cn.iocoder.jtyf.module.product.convert.brand;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.module.product.controller.admin.brand.vo.ProductBrandCreateReqVO;
import cn.iocoder.jtyf.module.product.controller.admin.brand.vo.ProductBrandRespVO;
import cn.iocoder.jtyf.module.product.controller.admin.brand.vo.ProductBrandSimpleRespVO;
import cn.iocoder.jtyf.module.product.controller.admin.brand.vo.ProductBrandUpdateReqVO;
import cn.iocoder.jtyf.module.product.dal.dataobject.brand.ProductBrandDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 品牌 Convert
 *
 * @author 九天芸菲
 */
@Mapper
public interface ProductBrandConvert {

    ProductBrandConvert INSTANCE = Mappers.getMapper(ProductBrandConvert.class);

    ProductBrandDO convert(ProductBrandCreateReqVO bean);

    ProductBrandDO convert(ProductBrandUpdateReqVO bean);

    ProductBrandRespVO convert(ProductBrandDO bean);

    List<ProductBrandSimpleRespVO> convertList1(List<ProductBrandDO> list);

    List<ProductBrandRespVO> convertList(List<ProductBrandDO> list);

    PageResult<ProductBrandRespVO> convertPage(PageResult<ProductBrandDO> page);

}
