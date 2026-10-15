package cn.iocoder.wx.module.vegetation.controller;

import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskResponse;
import cn.iocoder.wx.module.vegetation.api.VegetationPublicationResponse;
import cn.iocoder.wx.module.vegetation.service.VegetationPublicationService;
import cn.iocoder.wx.module.vegetation.service.VegetationTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** 对前端暴露任务编排、正式发布和版本回滚接口。 */
@RestController
@RequestMapping("/admin-api/vegetation/generation-task")
public class VegetationTaskController {

    private final VegetationTaskService taskService;
    private final VegetationPublicationService publicationService;

    public VegetationTaskController(VegetationTaskService taskService,
                                    VegetationPublicationService publicationService) {
        this.taskService = taskService;
        this.publicationService = publicationService;
    }

    @PostMapping("/create")
    public ResponseEntity<VegetationTaskResponse> create(@Valid @RequestBody VegetationGenerationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(taskService.create(request));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<VegetationTaskResponse> get(@PathVariable String taskId) {
        VegetationTaskResponse response = taskService.get(taskId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<VegetationTaskResponse> cancel(@PathVariable String taskId) {
        VegetationTaskResponse response = taskService.cancel(taskId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    /** 生成成功后显式发布；生成与发布分离，避免未审核瓦片直接替换线上版本。 */
    @PostMapping("/{taskId}/publish")
    public ResponseEntity<VegetationPublicationResponse> publish(@PathVariable String taskId) {
        return ResponseEntity.ok(publicationService.publish(taskId));
    }

    /** 查询场景当前稳定版本，供编辑器恢复后直接绑定Cesium图层。 */
    @GetMapping("/scene/{sceneId}/current")
    public ResponseEntity<VegetationPublicationResponse> current(@PathVariable String sceneId) {
        VegetationPublicationResponse response = publicationService.current(sceneId);
        return response == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(response);
    }

    /** 查询场景发布历史，版本切换页面只读取数据库索引，不扫描对象存储目录。 */
    @GetMapping("/scene/{sceneId}/history")
    public ResponseEntity<java.util.List<VegetationPublicationResponse>> history(@PathVariable String sceneId) {
        return ResponseEntity.ok(publicationService.list(sceneId));
    }

    /** 回滚只切换当前版本状态，历史对象仍保留以便审计和再次恢复。 */
    @PostMapping("/scene/{sceneId}/rollback")
    public ResponseEntity<VegetationPublicationResponse> rollback(
            @PathVariable String sceneId, @RequestParam String publicationVersion) {
        return ResponseEntity.ok(publicationService.rollback(sceneId, publicationVersion));
    }

    /** 业务组合校验失败属于客户端参数错误，不能作为服务端异常返回。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRule(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
