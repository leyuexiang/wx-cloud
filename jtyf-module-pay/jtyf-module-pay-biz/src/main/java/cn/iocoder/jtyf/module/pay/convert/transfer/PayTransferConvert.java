package cn.iocoder.jtyf.module.pay.convert.transfer;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO;
import cn.iocoder.jtyf.module.pay.api.transfer.dto.PayTransferCreateReqDTO;
import cn.iocoder.jtyf.module.pay.controller.admin.demo.vo.transfer.PayDemoTransferCreateReqVO;
import cn.iocoder.jtyf.module.pay.controller.admin.transfer.vo.PayTransferCreateReqVO;
import cn.iocoder.jtyf.module.pay.controller.admin.transfer.vo.PayTransferPageItemRespVO;
import cn.iocoder.jtyf.module.pay.controller.admin.transfer.vo.PayTransferRespVO;
import cn.iocoder.jtyf.module.pay.dal.dataobject.transfer.PayTransferDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PayTransferConvert {

    PayTransferConvert INSTANCE = Mappers.getMapper(PayTransferConvert.class);

    PayTransferDO convert(PayTransferCreateReqDTO dto);

    PayTransferUnifiedReqDTO convert2(PayTransferDO dto);

    PayTransferCreateReqDTO convert(PayTransferCreateReqVO vo);

    PayTransferCreateReqDTO convert(PayDemoTransferCreateReqVO vo);

    PayTransferRespVO  convert(PayTransferDO bean);

    PageResult<PayTransferPageItemRespVO> convertPage(PageResult<PayTransferDO> pageResult);
}
