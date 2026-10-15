package cn.iocoder.jtyf.module.dimensions.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.jtyf.module.dimensions.entity.Engineering;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EngineeringMapper extends BaseMapper<Engineering> {

    Integer findMaxId();

}
