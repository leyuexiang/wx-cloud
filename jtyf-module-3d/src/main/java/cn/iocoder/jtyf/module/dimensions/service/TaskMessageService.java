package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.entity.TaskMessageDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TaskMessageService extends IService<TaskMessageDO> {

     Boolean saveMessage(String message);
}
