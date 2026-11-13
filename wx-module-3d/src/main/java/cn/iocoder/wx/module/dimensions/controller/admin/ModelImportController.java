package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.*;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 导入入口沿用场景列表的全局令牌认证和 ss 方法权限，不在此解析令牌或建立会话。
 * 新增、替换共用入口，服务再按原操作类型细分既有权限；资源归属仍由受控服务核对。
 * 路由默认关闭，工作线程和发布适配完成前不得用于正式新增。
 */
@RestController
@RequestMapping("/dim3d/model-imports")
@ConditionalOnProperty(name="dim3d.model-import.enabled",havingValue="true")
public class ModelImportController {
    @Resource private ModelImportService service;
    @Resource private ArtResourceManifestVerifier parser;
    @PostMapping
    @PreAuthorize("@ss.hasAnyPermissions('dim3d:model:add','dim3d:model:edit')")
    public CommonResult<?> create(@RequestBody byte[] body) { return CommonResult.success(service.create(parser.parse(body))); }
    @GetMapping("/{operationId}")
    @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<?> get(@PathVariable String operationId) { return CommonResult.success(service.get(operationId)); }
    @PostMapping("/{operationId}/source-target")
    @PreAuthorize("@ss.hasAnyPermissions('dim3d:model:add','dim3d:model:edit')")
    public CommonResult<?> sourceTarget(@PathVariable String operationId,@RequestBody byte[] body) {
        empty(body);return CommonResult.success(service.sourceTarget(operationId));
    }
    @PostMapping("/{operationId}/submit")
    @PreAuthorize("@ss.hasAnyPermissions('dim3d:model:add','dim3d:model:edit')")
    public ResponseEntity<?> submit(@PathVariable String operationId,@RequestBody byte[] body) {
        empty(body);var result=service.submit(operationId);
        return ResponseEntity.status("COMMITTED".equals(result.importStatus())?200:202).body(CommonResult.success(result));
    }
    /** 只固定目录输入，实际发布由受控工作线程完成。 */
    @PostMapping("/{operationId}/publish")
    @PreAuthorize("@ss.hasAnyPermissions('dim3d:model:add','dim3d:model:edit')")
    public ResponseEntity<?> publish(@PathVariable String operationId,@RequestBody byte[] body) {
        var result=service.requestPublish(operationId,parser.parse(body));
        return ResponseEntity.status("COMMITTED".equals(result.importStatus())?200:202).body(CommonResult.success(result));
    }
    private void empty(byte[] body) {
        var request=parser.parse(body);
        if(!request.isObject() || !request.isEmpty()) throw new ServiceException(400,"此步骤只接受空对象");
    }
}
