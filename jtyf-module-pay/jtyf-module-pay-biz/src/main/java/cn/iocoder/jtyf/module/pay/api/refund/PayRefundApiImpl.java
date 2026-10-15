package cn.iocoder.jtyf.module.pay.api.refund;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.jtyf.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.jtyf.module.pay.convert.refund.PayRefundConvert;
import cn.iocoder.jtyf.module.pay.service.refund.PayRefundService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.jtyf.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class PayRefundApiImpl implements PayRefundApi {

    @Resource
    private PayRefundService payRefundService;

    @Override
    public CommonResult<Long> createRefund(PayRefundCreateReqDTO reqDTO) {
        return success(payRefundService.createPayRefund(reqDTO));
    }

    @Override
    public CommonResult<PayRefundRespDTO> getRefund(Long id) {
        return success(PayRefundConvert.INSTANCE.convert02(payRefundService.getRefund(id)));
    }

}
