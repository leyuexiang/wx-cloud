package cn.iocoder.wx.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.framework.common.pojo.BatchOperateResult;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.dimensions.domain.vo.TaskEventVO;
import cn.iocoder.wx.module.dimensions.entity.TaskEvent;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.TaskEventMapper;
import cn.iocoder.wx.module.dimensions.service.TaskEventService;
import cn.iocoder.wx.module.dimensions.utils.JsonUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 任务事件服务实现类
 * 处理任务事件的CRUD、批量操作及JSON字段转换等逻辑
 */
@Slf4j
@Service
public class TaskEventServiceImpl extends ServiceImpl<TaskEventMapper, TaskEvent> implements TaskEventService {

    // 常量定义
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String EMPTY_JSON = "{}"; // 空JSON对象常量

    @Override
    public List<TaskEventVO> getTaskEventListByUniqueId(String uniqueId) {
        return listByCondition(query -> query.eq(TaskEvent::getUniqueId, uniqueId), ErrorCodeConstants.UUID_DATABASE_NOT_EXIST, uniqueId);
    }

    @Override
    public List<TaskEventVO> getTaskEventListBySceneId(Integer sceneId) {
        return listByCondition(query -> query.eq(TaskEvent::getSceneId, sceneId), ErrorCodeConstants.SCENE_ID_DATABASE_NOT_EXIST, sceneId);
    }

    @Override
    public List<TaskEventVO> getTaskEventListByTaskId(String taskId) {
        return listByCondition(query -> query.eq(TaskEvent::getTaskId, taskId), ErrorCodeConstants.TASK_ID_DATABASE_NOT_EXIST, taskId);
    }

    @Override
    public TaskEventVO getTaskEvent(Integer id) {
        return getEntityById(id, ErrorCodeConstants.ID_DATABASE_NOT_EXIST);
    }

    @Override
    public String insertTaskEvent(TaskEventVO vo) {
        if (vo == null) throw WxBusinessException.of(ErrorCodeConstants.PARAM_IS_NULL);

        TaskEvent taskEvent = BeanUtils.toBean(vo, TaskEvent.class);

        // 处理JSON字段
        processJsonField(taskEvent::getPathPoints, taskEvent::setPathPoints);
        processJsonField(taskEvent::getActiveNode, taskEvent::setActiveNode);
        processJsonField(taskEvent::getHiddenNode, taskEvent::setHiddenNode);

        // 处理externalTaskId
        if (StringUtils.isNotBlank(vo.getExternalTaskId())) {
            String[] codeArray = vo.getExternalTaskId().split("#", 2);
            taskEvent.setCommand(codeArray.length > 0 ? codeArray[0] : null);
            taskEvent.setDeviceCode(codeArray.length > 1 ? codeArray[1] : null);
        }

        // 校验必要字段
        if (StrUtil.isAllBlank(taskEvent.getTaskId(), taskEvent.getUniqueId())) throw WxBusinessException.of(ErrorCodeConstants.TASK_ID_UUID_NOT_EXIST);

        // 判断是新增还是更新
        boolean exists = lambdaQuery()
                .eq(TaskEvent::getTaskId, taskEvent.getTaskId())
                .eq(TaskEvent::getUniqueId, taskEvent.getUniqueId())
                .exists();

        return exists ? updateEntity(taskEvent) : insertEntity(taskEvent);
    }

    @Override
    public String deleteTaskEvent(String taskId) {
        int count = baseMapper.delete(Wrappers.<TaskEvent>lambdaQuery().eq(TaskEvent::getTaskId, taskId));
        if (count == 0) throw WxBusinessException.of(ErrorCodeConstants.TASK_ID_DATABASE_NOT_EXIST, taskId);

        log.info("删除任务事件成功，taskId: {}", taskId);
        return "删除完成！";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperateResult<TaskEventVO> batchInsertTaskEvent(Integer sceneId, List<TaskEventVO> taskEvents) {
        if (CollectionUtils.isEmpty(taskEvents)) throw WxBusinessException.of(ErrorCodeConstants.TASK_EVENTS_IS_NULL);

        // 预处理所有VO
        List<TaskEvent> entities = taskEvents.stream()
                .peek(vo -> vo.setSceneId(sceneId))
                .map(vo -> {
                    TaskEvent entity = BeanUtils.toBean(vo, TaskEvent.class);
                    processEntityJsonFields(entity);
                    return entity;
                })
                .collect(Collectors.toList());

        List<TaskEvent> insertEntities = entities.stream()
                .filter(vo -> vo.getId() == 0)
                .toList();

        List<TaskEvent> updateEntities = entities.stream()
                .filter(vo -> vo.getId() > 0)
                .toList();

        // 真正的批量插入（使用MyBatis-Plus的saveBatch）
        boolean successInsert = saveBatch(insertEntities);
        //批量更新
        boolean successUpdate = updateBatchById(updateEntities);

        // 处理批量插入和更新的结果
        BatchOperateResult<TaskEventVO> result = new BatchOperateResult<>();
        if (successInsert && successUpdate) {
            // 如果插入和更新都成功，返回成功的taskEvents
            result.setSuccessList(taskEvents);
            log.info("批量插入任务事件成功，共插入 {} 条记录，更新 {} 条记录", insertEntities.size(), updateEntities.size());
        } else {
            // 插入或更新失败时，记录失败信息
            Map<TaskEventVO, String> failMap = new HashMap<>();
            taskEvents.forEach(vo -> failMap.put(vo, successInsert ? "更新失败" : "插入失败"));

            result.setFailList(Collections.singletonList(failMap)); // 设置失败列表
            log.error("批量插入任务事件失败，插入 {} 条记录，更新 {} 条记录", insertEntities.size(), updateEntities.size());
        }

        return result;
    }

    @Override
    public String deleteTaskEventByCode(String command, String deviceCode) {
        int count = baseMapper.delete(Wrappers.<TaskEvent>lambdaQuery()
                .eq(TaskEvent::getCommand, command)
                .eq(TaskEvent::getDeviceCode, deviceCode));

        if (count == 0) throw WxBusinessException.of(ErrorCodeConstants.COMMAND_DEVICE_CODE_DATABASE_NOT_EXIST, command, deviceCode);

        log.info("删除任务事件成功，command: {}, deviceCode: {}", command, deviceCode);
        return "删除完成！";
    }

    @Override
    public List<TaskEventVO> getTaskEventListByCode(String command, String deviceCode) {
        return listByCondition(query -> query
                        .eq(TaskEvent::getCommand, command)
                        .eq(TaskEvent::getDeviceCode, deviceCode),
                ErrorCodeConstants.COMMAND_DEVICE_CODE_DATABASE_NOT_EXIST, command, deviceCode);
    }

    // ------------------------------ 私有工具方法 ------------------------------

    /**
     * 通用列表查询方法
     */
    private List<TaskEventVO> listByCondition(Function<LambdaQueryChainWrapper<TaskEvent>, LambdaQueryChainWrapper<TaskEvent>> queryFunction, ErrorCodeConstants errorCode, Object... params) {
        List<TaskEvent> list = queryFunction.apply(lambdaQuery()).list();
        if (CollectionUtils.isEmpty(list)) throw WxBusinessException.of(errorCode, params);

        return list.stream().map(this::convertTaskEvent).collect(Collectors.toList());
    }

    /**
     * 根据ID获取实体并转换为VO
     */
    private TaskEventVO getEntityById(Integer id, ErrorCodeConstants errorCode) {
        TaskEvent entity = getById(id);
        if (entity == null) throw WxBusinessException.of(errorCode, id);
        return convertTaskEvent(entity);
    }

    /**
     * 插入实体并返回操作结果
     */
    private String insertEntity(TaskEvent entity) {
        boolean success = save(entity);
        log.info("插入任务事件 {}: {}", success ? "成功" : "失败", entity.getTaskId());
        return success ? "添加成功" : "添加失败";
    }

    /**
     * 更新实体并返回操作结果
     */
    private String updateEntity(TaskEvent entity) {
        boolean success = updateById(entity);
        log.info("更新任务事件 {}: {}", success ? "成功" : "失败", entity.getTaskId());
        return success ? "更新成功" : "更新失败";
    }

    /**
     * 处理实体的JSON字段
     */
    private void processEntityJsonFields(TaskEvent entity) {
        processJsonField(entity::getPathPoints, entity::setPathPoints);
        processJsonField(entity::getActiveNode, entity::setActiveNode);
        processJsonField(entity::getHiddenNode, entity::setHiddenNode);
    }

    /**
     * 处理JSON字段转换
     */
    private void processJsonField(Supplier<Object> getter, Consumer<String> setter) {
        Object value = getter.get();
        if (value == null) {
            setter.accept(EMPTY_JSON);
            return;
        }

        if (value instanceof String && StringUtils.isBlank((String) value)) {
            setter.accept(EMPTY_JSON);
            return;
        }

        try {
            setter.accept(JsonUtils.convertObjectToJson(value));
        } catch (JsonProcessingException e) {
            log.error("JSON转换失败，字段值: {}", value, e);
            setter.accept(EMPTY_JSON);
        }
    }

    /**
     * 实体转VO
     */
    private TaskEventVO convertTaskEvent(TaskEvent taskEvent) {
        TaskEventVO vo = BeanUtils.toBean(taskEvent, TaskEventVO.class);
        vo.setPathPoints(parseJsonArray(taskEvent.getPathPoints()));
        vo.setActiveNode(parseJsonArray(taskEvent.getActiveNode()));
        vo.setHiddenNode(parseJsonArray(taskEvent.getHiddenNode()));
        return vo;
    }

    /**
     * 解析JSON数组
     */
    private String[] parseJsonArray(String json) {
        if (StringUtils.isBlank(json)) return new String[0];

        try {
            return OBJECT_MAPPER.readValue(json, String[].class);
        } catch (JsonProcessingException e) {
            log.error("JSON解析失败，原始值: [{}]", json, e);
            return new String[0];
        }
    }
}