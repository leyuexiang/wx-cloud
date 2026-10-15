package cn.iocoder.jtyf.module.promotion.service.coupon;

import cn.iocoder.jtyf.framework.common.enums.CommonStatusEnum;
import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.module.product.api.category.ProductCategoryApi;
import cn.iocoder.jtyf.module.product.api.spu.ProductSpuApi;
import cn.iocoder.jtyf.module.promotion.controller.admin.coupon.vo.template.CouponTemplateCreateReqVO;
import cn.iocoder.jtyf.module.promotion.controller.admin.coupon.vo.template.CouponTemplatePageReqVO;
import cn.iocoder.jtyf.module.promotion.controller.admin.coupon.vo.template.CouponTemplateUpdateReqVO;
import cn.iocoder.jtyf.module.promotion.convert.coupon.CouponTemplateConvert;
import cn.iocoder.jtyf.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.jtyf.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.jtyf.module.promotion.enums.common.PromotionProductScopeEnum;
import cn.iocoder.jtyf.module.promotion.enums.coupon.CouponTakeTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.jtyf.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.jtyf.module.promotion.enums.ErrorCodeConstants.COUPON_TEMPLATE_NOT_EXISTS;
import static cn.iocoder.jtyf.module.promotion.enums.ErrorCodeConstants.COUPON_TEMPLATE_TOTAL_COUNT_TOO_SMALL;

/**
 * 优惠劵模板 Service 实现类
 *
 * @author 九天芸菲
 */
@Service
@Validated
public class CouponTemplateServiceImpl implements CouponTemplateService {

    @Resource
    private CouponTemplateMapper couponTemplateMapper;

    @Resource
    private ProductCategoryApi productCategoryApi;
    @Resource
    private ProductSpuApi productSpuApi;

    @Override
    public Long createCouponTemplate(CouponTemplateCreateReqVO createReqVO) {
        // 校验商品范围
        validateProductScope(createReqVO.getProductScope(), createReqVO.getProductScopeValues());
        // 插入
        CouponTemplateDO couponTemplate = CouponTemplateConvert.INSTANCE.convert(createReqVO)
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        couponTemplateMapper.insert(couponTemplate);
        // 返回
        return couponTemplate.getId();
    }

    @Override
    public void updateCouponTemplate(CouponTemplateUpdateReqVO updateReqVO) {
        // 校验存在
        CouponTemplateDO couponTemplate = validateCouponTemplateExists(updateReqVO.getId());
        // 校验发放数量不能过小（仅在 CouponTakeTypeEnum.USER 用户领取时）
        if (CouponTakeTypeEnum.isUser(couponTemplate.getTakeType())
                && updateReqVO.getTotalCount() > 0 // 大于 0 的原因，是因为 -1 不限制
                && updateReqVO.getTotalCount() < couponTemplate.getTakeCount()) {
            throw exception(COUPON_TEMPLATE_TOTAL_COUNT_TOO_SMALL, couponTemplate.getTakeCount());
        }
        // 校验商品范围
        validateProductScope(updateReqVO.getProductScope(), updateReqVO.getProductScopeValues());

        // 更新
        CouponTemplateDO updateObj = CouponTemplateConvert.INSTANCE.convert(updateReqVO);
        couponTemplateMapper.updateById(updateObj);
    }

    @Override
    public void updateCouponTemplateStatus(Long id, Integer status) {
        // 校验存在
        validateCouponTemplateExists(id);
        // 更新
        couponTemplateMapper.updateById(new CouponTemplateDO().setId(id).setStatus(status));
    }

    @Override
    public void deleteCouponTemplate(Long id) {
        // 校验存在
        validateCouponTemplateExists(id);
        // 删除
        couponTemplateMapper.deleteById(id);
    }

    private CouponTemplateDO validateCouponTemplateExists(Long id) {
        CouponTemplateDO couponTemplate = couponTemplateMapper.selectById(id);
        if (couponTemplate == null) {
            throw exception(COUPON_TEMPLATE_NOT_EXISTS);
        }
        return couponTemplate;
    }

    private void validateProductScope(Integer productScope, List<Long> productScopeValues) {
        if (Objects.equals(PromotionProductScopeEnum.SPU.getScope(), productScope)) {
            productSpuApi.validateSpuList(productScopeValues).checkError();
        } else if (Objects.equals(PromotionProductScopeEnum.CATEGORY.getScope(), productScope)) {
            productCategoryApi.validateCategoryList(productScopeValues).checkError();
        }
    }

    @Override
    public CouponTemplateDO getCouponTemplate(Long id) {
        return couponTemplateMapper.selectById(id);
    }

    @Override
    public PageResult<CouponTemplateDO> getCouponTemplatePage(CouponTemplatePageReqVO pageReqVO) {
        return couponTemplateMapper.selectPage(pageReqVO);
    }

    @Override
    public void updateCouponTemplateTakeCount(Long id, int incrCount) {
        couponTemplateMapper.updateTakeCount(id, incrCount);
    }

    @Override
    public List<CouponTemplateDO> getCouponTemplateListByTakeType(CouponTakeTypeEnum takeType) {
        return couponTemplateMapper.selectListByTakeType(takeType.getType());
    }

    @Override
    public List<CouponTemplateDO> getCouponTemplateList(List<Integer> canTakeTypes, Integer productScope,
                                                        Long productScopeValue, Integer count) {
        return couponTemplateMapper.selectList(canTakeTypes, productScope, productScopeValue, count);
    }

    @Override
    public List<CouponTemplateDO> getCouponTemplateList(Collection<Long> ids) {
        return couponTemplateMapper.selectBatchIds(ids);
    }

}
