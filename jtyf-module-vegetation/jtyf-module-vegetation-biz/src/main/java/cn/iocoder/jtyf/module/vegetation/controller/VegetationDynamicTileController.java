package cn.iocoder.jtyf.module.vegetation.controller;

import cn.iocoder.jtyf.module.vegetation.api.VegetationDynamicTileWindowRequest;
import cn.iocoder.jtyf.module.vegetation.api.VegetationDynamicTileWindowResponse;
import cn.iocoder.jtyf.module.vegetation.service.VegetationDynamicTileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 动态九宫格独立入口；不与城市级生成任务控制器共用请求或发布接口。 */
@RestController
@RequestMapping("/admin-api/vegetation/dynamic-tile")
public class VegetationDynamicTileController {

    private final VegetationDynamicTileService dynamicTileService;

    public VegetationDynamicTileController(VegetationDynamicTileService dynamicTileService) {
        this.dynamicTileService = dynamicTileService;
    }

    /** 提交九块独立任务并返回每块状态；接口本身不等待Python生成完成。 */
    @PostMapping("/window")
    public ResponseEntity<VegetationDynamicTileWindowResponse> create(
            @Valid @RequestBody VegetationDynamicTileWindowRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dynamicTileService.create(request));
    }

    /** 窗口协议和缓存身份错误属于请求参数错误，不能影响其他静态植被接口。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidWindow(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
