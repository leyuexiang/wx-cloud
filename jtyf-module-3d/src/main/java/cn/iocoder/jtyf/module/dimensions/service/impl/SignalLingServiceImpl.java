package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.module.dimensions.entity.SignalLing;
import cn.iocoder.jtyf.module.dimensions.mapper.SignalLingMapper;
import cn.iocoder.jtyf.module.dimensions.service.SignalLingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SignalLingServiceImpl extends ServiceImpl<SignalLingMapper, SignalLing> implements SignalLingService {

    @Override
    public Map<String, String> getAddress() {
        List<SignalLing> signalLingList = list();
        Map<String, String> signalLingHashMap = new HashMap<>();
        for (SignalLing signalLing : signalLingList) {
            signalLingHashMap.put(signalLing.getProp(), signalLing.getValue());
        }
        return signalLingHashMap;
    }
}
