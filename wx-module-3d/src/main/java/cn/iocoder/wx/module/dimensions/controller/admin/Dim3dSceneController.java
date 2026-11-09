package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 新编辑器的场景身份、四包修订和租约入口；保留旧工程接口供原消费者使用。 */
@RestController
@RequestMapping("/dim3d/scene")
public class Dim3dSceneController {
    @Resource private Dim3dSceneService service;

    @GetMapping("/page")
    public CommonResult<Map<String, Object>> page(String sceneName, String sceneType, String sceneCategory, String sceneKind, Integer publishStatus, String tenantId,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        return CommonResult.success(service.page(sceneName, sceneType, sceneCategory, sceneKind, publishStatus, tenantId, pageNum, pageSize));
    }
    @GetMapping("/info/{id}")
    public CommonResult<Map<String, Object>> info(@PathVariable String id) { return CommonResult.success(service.info(id)); }

    /** 读取固定修订的区块实例；分片读取只读，不改变场景租约和完整四包。 */
    @GetMapping("/chunks/{id}")
    public CommonResult<Map<String, Object>> chunks(@PathVariable String id,
                                                     @RequestParam int revisionNo,
                                                     String chunkId,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "100") int pageSize,
                                                     @RequestParam(defaultValue = "true") boolean includeRelations) {
        return CommonResult.success(service.readChunks(id, revisionNo, chunkId, pageNum, pageSize, includeRelations));
    }

    // 复用已部署的平台/租户管理员角色；普通查看者只能查询本租户，不能保存或取得编辑租约。
    @PostMapping("/add") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> add(@RequestBody JsonNode body) { return CommonResult.success(service.create(body)); }
    @PutMapping("/edit/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> edit(@PathVariable String id, @RequestBody JsonNode body) { service.edit(id, body); return CommonResult.success(true); }
    @DeleteMapping("/remove/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> remove(@PathVariable String id) { service.remove(id); return CommonResult.success(true); }
    @PostMapping("/lock/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> lock(@PathVariable String id, @RequestParam String clientKey) { return CommonResult.success(service.lock(id, clientKey)); }
    @PostMapping("/heartbeat/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> heartbeat(@PathVariable String id, @RequestBody JsonNode body) { return CommonResult.success(service.heartbeat(id, body)); }
    @DeleteMapping("/unlock/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> unlock(@PathVariable String id, @RequestParam String clientKey) { service.unlock(id, clientKey); return CommonResult.success(true); }
    @PutMapping("/saveAll") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> save(@RequestBody JsonNode body) { return CommonResult.success(service.save(body)); }

    /** 从同一次采集的四包快照创建独立模板场景；旧文件型模板接口保持不变。 */
    @PostMapping("/saveAsTemplateFromSnapshot") @PreAuthorize("@ss.hasAnyRoles('super_admin')")
    public CommonResult<Map<String, Object>> saveAsTemplateFromSnapshot(@RequestBody JsonNode body) {
        return CommonResult.success(service.saveAsTemplateFromSnapshot(body));
    }

    /** 从快照模板创建新的独立场景；模板正文只读，实例场景拥有自己的编号和首版修订。 */
    @PostMapping("/fromTemplate") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> createFromTemplate(@RequestBody JsonNode body) {
        return CommonResult.success(service.createFromTemplate(body));
    }

    /** 快照模板的独立目录入口，旧文件型模板仍由资源目录控制器维护。 */
    @GetMapping("/template/page") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<PageResult<Map<String, Object>>> templatePage(String templateName, String templateType, String category,
                                                                        String status, Integer pageNum, Integer pageSize) {
        return CommonResult.success(service.templatePage(templateName, templateType, category, status, pageNum, pageSize));
    }

    @GetMapping("/template/info/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> templateInfo(@PathVariable String id) { return CommonResult.success(service.templateInfo(id)); }

    @PutMapping("/template/{status}/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> templateStatus(@PathVariable String status, @PathVariable String id, @RequestParam(required = false) String reason) {
        service.changeTemplateStatus(id, status, reason); return CommonResult.success(true);
    }

    @PostMapping("/template/publish/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> publishTemplate(@PathVariable String id, @RequestParam int versionNo, @RequestParam(required = false) String reason) {
        service.publishTemplate(id, versionNo, reason); return CommonResult.success(true);
    }

    @PostMapping("/template/rollback/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> rollbackTemplate(@PathVariable String id, @RequestParam int versionNo, @RequestParam(required = false) String reason) {
        service.rollbackTemplate(id, versionNo, reason); return CommonResult.success(true);
    }

    @PostMapping("/template/share/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> shareTemplate(@PathVariable String id, @RequestParam int versionNo, @RequestParam long targetTenantId,
                                               @RequestParam(required = false) String reason) {
        service.shareTemplate(id, versionNo, targetTenantId, reason); return CommonResult.success(true);
    }

    @DeleteMapping("/template/share/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> revokeTemplateShare(@PathVariable String id, @RequestParam int versionNo, @RequestParam long targetTenantId,
                                                     @RequestParam(required = false) String reason) {
        service.revokeTemplateShare(id, versionNo, targetTenantId, reason); return CommonResult.success(true);
    }

    @DeleteMapping("/template/remove/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> removeTemplate(@PathVariable String id, @RequestParam(required = false) String reason) {
        service.removeTemplate(id, reason); return CommonResult.success(true);
    }
}
