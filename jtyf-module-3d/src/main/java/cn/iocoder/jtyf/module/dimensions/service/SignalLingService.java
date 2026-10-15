package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.entity.SignalLing;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface SignalLingService extends IService<SignalLing> {

    Map<String,String> getAddress();
}
