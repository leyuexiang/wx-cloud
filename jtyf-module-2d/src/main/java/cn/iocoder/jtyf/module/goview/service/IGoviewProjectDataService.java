package cn.iocoder.jtyf.module.goview.service;

import cn.iocoder.jtyf.module.goview.model.GoviewProjectData;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IGoviewProjectDataService extends IService<GoviewProjectData> {

    public GoviewProjectData getProjectid(String projectId);

}
