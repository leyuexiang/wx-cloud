package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.module.dimensions.entity.TestData;
import cn.iocoder.wx.module.dimensions.mapper.TestDataMapper;
import cn.iocoder.wx.module.dimensions.service.TestDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class TestDataServiceImpl extends ServiceImpl<TestDataMapper, TestData> implements TestDataService {
}
