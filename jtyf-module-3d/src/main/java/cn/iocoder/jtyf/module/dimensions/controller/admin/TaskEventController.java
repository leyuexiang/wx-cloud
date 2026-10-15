package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.TaskEventVO;
import cn.iocoder.jtyf.module.dimensions.entity.TaskEvent;
import cn.iocoder.jtyf.module.dimensions.service.TaskEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/dimensions/task-event")
@Slf4j
@Tag(name = "任务事件接口",description = "任务事件接口")
public class TaskEventController {

    @Autowired
    TaskEventService taskEventService;


    @PostMapping("/insert-update-task-event")
    @Operation(summary = "插入或者更改任务事件",description = "插入或者更改任务事件")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:insert')")
    public CommonResult<String> insertTaskEvent(@RequestBody TaskEventVO taskEvent) {
        return CommonResult.success(taskEventService.insertTaskEvent(taskEvent));
    }

    @PostMapping("/batch-insert-update-task-event")
    @Operation(summary = "批量插入或者更改任务事件",description = "批量插入或者更改任务事件")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:batch')")
    public CommonResult<?> batchInsertTaskEvent(@RequestParam Integer sceneId, @RequestBody List<TaskEventVO> taskEvents) {
        return CommonResult.success(taskEventService.batchInsertTaskEvent(sceneId, taskEvents));
    }

    @DeleteMapping("/delete-task-event")
    @Operation(summary = "删除任务事件")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:deleteTaskEvent')")
    public CommonResult<String> deleteTaskEvent(@RequestParam String taskId) {
        return CommonResult.success(taskEventService.deleteTaskEvent(taskId));
    }

    @DeleteMapping("/delete-task-event-by-code")
    @Operation(summary = "根据任务code和设备code,删除任务事件")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:deleteTaskEventByCode')")
    public CommonResult<String> deleteTaskEventByCode(@RequestParam String command, @RequestParam String deviceCode) {
        return CommonResult.success(taskEventService.deleteTaskEventByCode(command,deviceCode));
    }

    @GetMapping("/get-task-event-uniqueId")
    @Operation(summary = "根据uniqueId获取任务事件列表")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getTaskEventListByUniqueId')")
    public ListResult<TaskEventVO> getTaskEventListByUniqueId(@RequestParam String uniqueId) {
        return ListResult.success(taskEventService.getTaskEventListByUniqueId(uniqueId));
    }

    @GetMapping("/get-task-event-sceneId")
    @Operation(summary = "根据sceneId获取任务事件列表")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getTaskEventListBySceneId')")
    public ListResult<TaskEventVO> getTaskEventListBySceneId(@RequestParam Integer sceneId) {
        return ListResult.success(taskEventService.getTaskEventListBySceneId(sceneId));
    }

    @GetMapping("/get-task-event-taskId")
    @Operation(summary = "根据taskId获取任务事件列表")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getTaskEventListByTaskId')")
    public ListResult<TaskEventVO> getTaskEventListByTaskId(@RequestParam String taskId) {
        return ListResult.success(taskEventService.getTaskEventListByTaskId(taskId));
    }

    @GetMapping("/get-task-event-code")
    @Operation(summary = "根据任务code和设备code获取任务事件列表")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getTaskEventListByCode')")
    public ListResult<TaskEventVO> getTaskEventListByCode(@RequestParam String command, @RequestParam String deviceCode) {
        return ListResult.success(taskEventService.getTaskEventListByCode(command,deviceCode));
    }

    @GetMapping("/get-task-event")
    @Operation(summary = "根据id获取任务事件详情")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getTaskEvent')")
    public CommonResult<TaskEventVO> getTaskEvent(@RequestParam Integer id) {
        return CommonResult.success(taskEventService.getTaskEvent(id));
    }

    @GetMapping("/get-all-task-event")
    @Operation(summary = "获取所有任务事件列表")
    @PreAuthorize("@ss.hasPermission('dimensions:taskEvent:getAllTaskEvent')")
    public ListResult<TaskEvent> getAllTaskEvent() {
        return ListResult.success(taskEventService.list());
    }

}
