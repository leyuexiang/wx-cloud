package cn.iocoder.wx.module.pay.convert.demo;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.pay.controller.admin.demo.vo.order.PayDemoOrderCreateReqVO;
import cn.iocoder.wx.module.pay.controller.admin.demo.vo.order.PayDemoOrderRespVO;
import cn.iocoder.wx.module.pay.dal.dataobject.demo.PayDemoOrderDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 示例订单 Convert
 *
 * @author 九天芸菲
 */
@Mapper
public interface PayDemoOrderConvert {

    PayDemoOrderConvert INSTANCE = Mappers.getMapper(PayDemoOrderConvert.class);

    PayDemoOrderDO convert(PayDemoOrderCreateReqVO bean);

    PayDemoOrderRespVO convert(PayDemoOrderDO bean);

    PageResult<PayDemoOrderRespVO> convertPage(PageResult<PayDemoOrderDO> page);

}
