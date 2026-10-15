package cn.iocoder.jtyf.module.dimensions.service.impl;


import cn.iocoder.jtyf.module.dimensions.entity.DeviceStatusPpfmter;
import cn.iocoder.jtyf.module.dimensions.mapper.ComponentStatusPpfmterMapper;
import cn.iocoder.jtyf.module.dimensions.service.AlertComponentStatusPpfmterService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AlertComponentStatusPpfmterServiceImpl extends ServiceImpl<ComponentStatusPpfmterMapper, DeviceStatusPpfmter> implements AlertComponentStatusPpfmterService {
}
