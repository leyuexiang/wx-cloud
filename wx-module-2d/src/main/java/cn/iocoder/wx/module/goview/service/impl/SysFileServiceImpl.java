package cn.iocoder.wx.module.goview.service.impl;


import cn.iocoder.wx.module.goview.mapper.SysFileMapper;
import cn.iocoder.wx.module.goview.model.SysFile;
import cn.iocoder.wx.module.goview.service.ISysFileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysFileServiceImpl extends ServiceImpl<SysFileMapper, SysFile> implements ISysFileService {
    @Autowired
    private SysFileMapper sysFileMapper;

    @Override
    public SysFile selectByExamplefileName(String filename) {
        SysFile sysFile = sysFileMapper.selectOne(new LambdaQueryWrapper<SysFile>().eq(SysFile::getFileName, filename));
        return sysFile;
    }

}
