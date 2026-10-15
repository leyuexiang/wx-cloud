package cn.iocoder.jtyf.module.dimensions.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.jtyf.module.dimensions.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {

    void updateAllHasNext();

    void updateHasNext();

    void updateLevelUniqueId(@Param("parentId") Integer parentId);

}