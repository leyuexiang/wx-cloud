package cn.iocoder.wx.module.dimensions.service.impl;


import cn.iocoder.wx.module.dimensions.entity.DeviceStatusPpfmter;
import cn.iocoder.wx.module.dimensions.mapper.ComponentStatusPpfmterMapper;
import cn.iocoder.wx.module.dimensions.service.AlertComponentStatusPpfmterService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AlertComponentStatusPpfmterServiceImpl extends ServiceImpl<ComponentStatusPpfmterMapper, DeviceStatusPpfmter> implements AlertComponentStatusPpfmterService {
}
