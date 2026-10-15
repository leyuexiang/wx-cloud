package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.jtyf.module.dimensions.entity.TaskMessageDO;
import cn.iocoder.jtyf.module.dimensions.mapper.TaskMessageMapper;
import cn.iocoder.jtyf.module.dimensions.service.TaskMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TaskMessageServiceImpl extends ServiceImpl<TaskMessageMapper, TaskMessageDO> implements TaskMessageService {

    @Override
    public Boolean saveMessage(String message) {
        if (StrUtil.isEmpty(message)) return true;
        JSONObject result = JSONUtil.parseObj(message);
        JSONObject data = JSONUtil.parseObj(result.get("data"));
        String taskId = data.getStr("taskId");
        String version = result.getStr("version");
        String type = result.getStr("type");
        String businessModule = result.getStr("businessModule");
        TaskMessageDO taskMessageDO = TaskMessageDO.builder().build()
                .setTaskId(taskId)
                .setType(type)
                .setVersion(version)
                .setCreateTime(LocalDateTime.now())
                .setData(JSONUtil.toJsonStr(data))
                .setBusinessModule(businessModule);
        return save(taskMessageDO);
    }
}
