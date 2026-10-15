package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.module.dimensions.entity.TestData;
import cn.iocoder.jtyf.module.dimensions.mapper.TestDataMapper;
import cn.iocoder.jtyf.module.dimensions.service.TestDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TestDataServiceImpl extends ServiceImpl<TestDataMapper, TestData> implements TestDataService {
}
