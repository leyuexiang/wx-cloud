package cn.iocoder.jtyf.module.goview.service.impl;

import cn.iocoder.jtyf.module.goview.mapper.GoviewProjectDataMapper;
import cn.iocoder.jtyf.module.goview.model.GoviewProjectData;
import cn.iocoder.jtyf.module.goview.service.IGoviewProjectDataService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class GoviewProjectDataServiceImpl extends ServiceImpl<GoviewProjectDataMapper, GoviewProjectData> implements IGoviewProjectDataService {
    @Autowired
    GoviewProjectDataMapper dataMapper;

    @Override
    public GoviewProjectData getProjectid(String projectId) {
        LambdaQueryWrapper<GoviewProjectData> lambdaQueryWrapper = new LambdaQueryWrapper<GoviewProjectData>();
        lambdaQueryWrapper.eq(GoviewProjectData::getProjectId, projectId);
        return dataMapper.selectOne(lambdaQueryWrapper);

    }

}
