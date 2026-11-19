package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dScenePageResponse;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationService;
import cn.iocoder.wx.module.dimensions.service.ScenePublicationTicketService;
import cn.iocoder.wx.module.dimensions.service.SceneTenantGrantService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.List;

/** 新编辑器的场景身份、四包修订和租约入口；保留旧工程接口供原消费者使用。 */
@RestController
@RequestMapping("/dim3d/scene")
public class Dim3dSceneController {
    @Resource private Dim3dSceneService service;
    @Resource private ScenePublicationService publicationService;
    @Resource private ScenePublicationTicketService publicationTicketService;
    @Resource private SceneTenantGrantService grants;

    /** 授权关系只由平台管理员维护，服务层复核来源和可信租户身份。 */
    public record SceneGrantForm(String sceneId, String grantTenantId) { }

    @PostMapping("/grant") @PreAuthorize("@ss.hasAnyRoles('super_admin')")
    public CommonResult<Void> grant(@RequestBody SceneGrantForm body) {
        grants.grant(body.sceneId(), body.grantTenantId());
        return CommonResult.success(null);
    }

    @PostMapping("/revoke") @PreAuthorize("@ss.hasAnyRoles('super_admin')")
    public CommonResult<Void> revoke(@RequestBody SceneGrantForm body) {
        grants.revoke(body.sceneId(), body.grantTenantId());
        return CommonResult.success(null);
    }

    @GetMapping("/authorizedTenants/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin')")
    public CommonResult<List<Map<String, Object>>> authorizedTenants(@PathVariable String id) {
        return CommonResult.success(grants.list(id));
    }

    @GetMapping("/page")
    public Dim3dScenePageResponse page(String sceneName, String sceneType, String sceneCategory, String sceneKind,
            Integer publishStatus, String tenantId, String orderByColumn, String isAsc,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) {
        return new Dim3dScenePageResponse(service.page(sceneName, sceneType, sceneCategory, sceneKind, publishStatus,
                tenantId, pageNum, pageSize, orderByColumn, isAsc));
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
    public CommonResult<Void> edit(@PathVariable String id, @RequestBody JsonNode body) { service.edit(id, body); return CommonResult.success(null); }
    @DeleteMapping("/remove/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Void> remove(@PathVariable String id) { service.remove(id); return CommonResult.success(null); }
    /** 复制的是当前已保存四包，新场景由服务端生成独立身份。 */
    @PostMapping("/copy/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> copy(@PathVariable String id) { return CommonResult.success(service.copy(id)); }
    @PostMapping("/lock/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> lock(@PathVariable String id, @RequestParam String clientKey) { return CommonResult.success(service.lock(id, clientKey)); }
    @PostMapping("/heartbeat/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> heartbeat(@PathVariable String id, @RequestBody JsonNode body) { return CommonResult.success(service.heartbeat(id, body)); }
    @DeleteMapping("/unlock/{id}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Void> unlock(@PathVariable String id, @RequestParam String clientKey) { service.unlock(id, clientKey); return CommonResult.success(null); }
    @PutMapping("/saveAll") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> save(@RequestBody JsonNode body) { return CommonResult.success(service.save(body)); }

    /** 固定修订的显式保留引用，身份和期限均在服务层验证。 */
    @PostMapping("/bookmark/{sceneId}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> createBookmark(@PathVariable String sceneId, @RequestBody JsonNode body) {
        return CommonResult.success(service.createBookmark(sceneId, body));
    }

    /** 仅撤销当前用户自己的书签，历史行保留用于审计。 */
    @DeleteMapping("/bookmark/{sceneId}/{bookmarkId}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Void> revokeBookmark(@PathVariable String sceneId, @PathVariable String bookmarkId) {
        service.revokeBookmark(sceneId, bookmarkId); return CommonResult.success(null);
    }

    /** 发布接口只固定已有修订，查询和撤销均受当前租户身份约束。 */
    @PostMapping("/publication/publish") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> publish(@RequestParam String sceneId, @RequestParam int revisionNo, @RequestParam String requestId) {
        return CommonResult.success(publicationService.publish(sceneId, revisionNo, requestId));
    }

    /** 告知管理页当前服务支持的受控访问方式和真实票据时限。 */
    @GetMapping("/publication/capabilities") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> publicationCapabilities() {
        return CommonResult.success(publicationService.capabilities());
    }

    /** 首次确认时锁内检查编辑书签，再走既有不可变发布事务。 */
    @PostMapping("/publication/publish-current") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> publishCurrent(@RequestParam String sceneId, @RequestParam int revisionNo,
                                                            @RequestParam String requestId) {
        return CommonResult.success(publicationService.publishCurrent(sceneId, revisionNo, requestId));
    }

    @GetMapping("/publication/current/{sceneId}")
    public CommonResult<Map<String, Object>> currentPublication(@PathVariable String sceneId) {
        return CommonResult.success(publicationService.current(sceneId));
    }

    @GetMapping("/publication/{publicationId}")
    public CommonResult<Map<String, Object>> publication(@PathVariable String publicationId) {
        return CommonResult.success(publicationService.read(publicationId));
    }

    @GetMapping("/publication/{publicationId}/snapshot")
    public CommonResult<Map<String, Object>> publicationSnapshot(@PathVariable String publicationId) {
        return CommonResult.success(publicationService.readSnapshot(publicationId));
    }

    @PostMapping("/publication/{publicationId}/ticket") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<Map<String, Object>> issuePublicationTicket(@PathVariable String publicationId,
                                                                      @RequestParam(defaultValue = "" + ScenePublicationTicketService.DEFAULT_SECONDS) int expiresInSeconds,
                                                                      HttpServletResponse response) {
        // 明文票据只交付当前页面内存，禁止共享缓存和访问日志记录响应。
        response.setHeader("Cache-Control", "no-store");
        return CommonResult.success(publicationTicketService.issue(publicationId, expiresInSeconds));
    }

    @DeleteMapping("/publication/ticket/{ticketId}") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> revokePublicationTicket(@PathVariable String ticketId) {
        publicationTicketService.revoke(ticketId); return CommonResult.success(true);
    }

    @PostMapping("/publication/{publicationId}/revoke") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Boolean> revokePublication(@PathVariable String publicationId, @RequestParam(required = false) String reason) {
        publicationService.revoke(publicationId, reason); return CommonResult.success(true);
    }

    @PostMapping("/publication/{publicationId}/rollback") @PreAuthorize("@ss.hasAnyRoles('super_admin','tenant_admin')")
    public CommonResult<Map<String, Object>> rollbackPublication(@PathVariable String publicationId, @RequestParam String requestId) {
        return CommonResult.success(publicationService.rollback(publicationId, requestId));
    }

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
