package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.entity.TaskMessageDO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TaskMessageService extends IService<TaskMessageDO> {

     Boolean saveMessage(String message);
}
