package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.framework.common.pojo.BatchOperateResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.TaskEventVO;
import cn.iocoder.jtyf.module.dimensions.entity.TaskEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface TaskEventService extends IService<TaskEvent> {
    List<TaskEventVO> getTaskEventListByUniqueId(String uniqueId);

    List<TaskEventVO> getTaskEventListBySceneId(Integer sceneId);

    List<TaskEventVO> getTaskEventListByTaskId(String taskId);

    TaskEventVO getTaskEvent(Integer id);

    String insertTaskEvent(TaskEventVO taskEvent);

    String deleteTaskEvent(String taskId);

    BatchOperateResult<TaskEventVO> batchInsertTaskEvent(Integer sceneId, List<TaskEventVO> taskEvents);

    String deleteTaskEventByCode(String command, String deviceCode);

    List<TaskEventVO> getTaskEventListByCode(String command, String deviceCode);
}
