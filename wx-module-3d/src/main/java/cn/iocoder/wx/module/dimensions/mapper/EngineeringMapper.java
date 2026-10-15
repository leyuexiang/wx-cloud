package cn.iocoder.wx.module.dimensions.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.wx.module.dimensions.entity.Engineering;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EngineeringMapper extends BaseMapper<Engineering> {

    Integer findMaxId();

}
