package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dimensions/task-message")
@Slf4j
@Tag(name = "任务接口", description = "任务接口")
public class TaskMessageController {

    /**
     * 获取任务列表
     *
     * @return CommonResult<List < Map>> 包含任务列表数据的 CommonResult 对象
     */
    @GetMapping("/query-task-message-list")
    @Operation(summary = "获取任务列表")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:taskMessage:queryTaskMessageList')")
    public ListResult<Map> queryTaskMessageList() {
        try {
            List<Map> dataVoList = new ArrayList<>();
            processTaskList(readAndParseJson("demo_datas/taskData.json"), dataVoList);
            return ListResult.success(dataVoList);
        } catch (Exception e) {
            log.error("获取任务列表失败，错误信息: {}", e.getMessage());
            throw JtyfBusinessException.of(ErrorCodeConstants.GET_TASK_LIST_FAIL);
        }
    }

    /**
     * 获取新的任务列表
     *
     * @return CommonResult<List < Map>> 包含新任务列表数据的 CommonResult 对象
     */
    @GetMapping("/query-task-message-list-new")
    @Operation(summary = "获取任务列表(新)")
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("@ss.hasPermission('dimensions:taskMessage:queryTaskMessageListNew')")
    public ListResult<Map> queryTaskMessageListNew() {
        try {
            List<Map> dataVoList = new ArrayList<>();
            processTaskListNew(readAndParseJson("demo_datas/taskData_new.json"), dataVoList);
            return ListResult.success(dataVoList);
        } catch (Exception e) {
            log.error("获取新任务列表失败，错误信息: {}", e.getMessage());
            throw JtyfBusinessException.of(ErrorCodeConstants.GET_NEW_TASK_LIST_FAIL);
        }
    }

    /**
     * 处理任务列表数据
     *
     * @param list       原始任务列表数据
     * @param dataVoList 处理后的任务列表数据
     */
    private void processTaskList(List<Map> list, List<Map> dataVoList) {
        list.forEach(map -> dataVoList.add((Map) map.get("data")));
    }

    /**
     * 处理新的任务列表数据
     *
     * @param list       原始新任务列表数据
     * @param dataVoList 处理后的新任务列表数据
     */
    private void processTaskListNew(List<Map> list, List<Map> dataVoList) {
        list.forEach(map -> {
            map.put("externalTaskId", map.get("command") + "#" + map.get("deviceCode"));
            dataVoList.add(map);
        });
    }

    /**
     * 读取并解析 JSON 文件
     *
     * @param filePath JSON 文件路径
     * @return List<Map> 解析后的 JSON 数据列表
     * @throws IOException 如果读取或解析 JSON 文件失败
     */
    private List<Map> readAndParseJson(String filePath) throws IOException {
        try (InputStream templateInputStream = TaskMessageController.class.getClassLoader().getResourceAsStream(filePath)) {
            if (templateInputStream == null) {
                throw new IOException("无法找到文件: " + filePath);
            }
            String content = new String(templateInputStream.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(content);
            String jsonStr = jsonObject.getJSONArray("data").toString();
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonStr, new TypeReference<>() {
            });
        }
    }
}