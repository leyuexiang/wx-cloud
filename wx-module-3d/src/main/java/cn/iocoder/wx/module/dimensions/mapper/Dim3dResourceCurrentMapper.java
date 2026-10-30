package cn.iocoder.wx.module.dimensions.mapper;

import cn.iocoder.wx.module.dimensions.entity.Dim3dResourceCurrent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 当前内容映射；全局查询须在后续专用服务中显式鉴权并限定范围。 */
@Mapper
public interface Dim3dResourceCurrentMapper extends BaseMapper<Dim3dResourceCurrent> {
}
