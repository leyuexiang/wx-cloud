package cn.iocoder.wx.module.dimensions.mapper;

import cn.iocoder.wx.module.dimensions.entity.Dim3dResourceOperation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 操作记录映射；不能依靠客户端操作编号跳过资源范围及操作者校验。 */
@Mapper
public interface Dim3dResourceOperationMapper extends BaseMapper<Dim3dResourceOperation> {
}
