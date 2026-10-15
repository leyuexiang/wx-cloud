package cn.iocoder.wx.module.pay.convert.wallet;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.pay.controller.admin.wallet.vo.transaction.PayWalletTransactionRespVO;
import cn.iocoder.wx.module.pay.dal.dataobject.wallet.PayWalletTransactionDO;
import cn.iocoder.wx.module.pay.service.wallet.bo.WalletTransactionCreateReqBO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface PayWalletTransactionConvert {

    PayWalletTransactionConvert INSTANCE = Mappers.getMapper(PayWalletTransactionConvert.class);

    PageResult<PayWalletTransactionRespVO> convertPage2(PageResult<PayWalletTransactionDO> page);

    PayWalletTransactionDO convert(WalletTransactionCreateReqBO bean);

}
