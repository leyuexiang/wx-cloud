package cn.iocoder.wx.module.dimensions.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.wx.module.dimensions.entity.FileOnlyInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileOnlyInfoMapper extends BaseMapper<FileOnlyInfo> {

    void updateUniqueId(@Param("parentId") Integer parentId);

}
