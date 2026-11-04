package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.*;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 接收侧路由默认不注册；工作线程和发布适配完成前不得开启此功能。 */
@RestController
@RequestMapping("/dim3d/model-imports")
@ConditionalOnProperty(name="dim3d.model-import.enabled",havingValue="true")
public class ModelImportController {
    @Resource private ModelImportService service;
    @Resource private ArtResourceManifestVerifier parser;
    @PostMapping
    public CommonResult<?> create(@RequestBody byte[] body) { return CommonResult.success(service.create(parser.parse(body))); }
    @GetMapping("/{operationId}")
    public CommonResult<?> get(@PathVariable String operationId) { return CommonResult.success(service.get(operationId)); }
    @PostMapping("/{operationId}/source-target")
    public CommonResult<?> sourceTarget(@PathVariable String operationId,@RequestBody byte[] body) {
        empty(body);return CommonResult.success(service.sourceTarget(operationId));
    }
    @PostMapping("/{operationId}/submit")
    public ResponseEntity<?> submit(@PathVariable String operationId,@RequestBody byte[] body) {
        empty(body);var result=service.submit(operationId);
        return ResponseEntity.status("COMMITTED".equals(result.importStatus())?200:202).body(CommonResult.success(result));
    }
    /** 只固定目录输入，实际发布由受控工作线程完成。 */
    @PostMapping("/{operationId}/publish")
    public ResponseEntity<?> publish(@PathVariable String operationId,@RequestBody byte[] body) {
        var result=service.requestPublish(operationId,parser.parse(body));
        return ResponseEntity.status("COMMITTED".equals(result.importStatus())?200:202).body(CommonResult.success(result));
    }
    private void empty(byte[] body) {
        var request=parser.parse(body);
        if(!request.isObject() || !request.isEmpty()) throw new ServiceException(400,"此步骤只接受空对象");
    }
}
