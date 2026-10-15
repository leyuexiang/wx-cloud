package cn.iocoder.jtyf.module.pay.dal.mysql.demo;

import cn.iocoder.jtyf.framework.common.pojo.PageParam;
import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.jtyf.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.jtyf.module.pay.dal.dataobject.demo.PayDemoTransferDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PayDemoTransferMapper extends BaseMapperX<PayDemoTransferDO> {

    default  PageResult<PayDemoTransferDO> selectPage(PageParam pageParam){
        return selectPage(pageParam, new LambdaQueryWrapperX<PayDemoTransferDO>()
                .orderByDesc(PayDemoTransferDO::getId));
    }
}