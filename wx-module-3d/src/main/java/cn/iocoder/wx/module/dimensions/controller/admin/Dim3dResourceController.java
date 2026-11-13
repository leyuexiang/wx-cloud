package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.validation.ValidationUtils;
import cn.iocoder.wx.module.dimensions.service.ModelImportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import cn.iocoder.wx.module.dimensions.service.Dim3dResourceCatalogService;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


/**
 * 新资源管理接口。
 * 路径与 operation.openapi 中的模型、图标、模板协议保持一致，旧项目资源接口不受影响。
 */
@Validated
@RestController
@RequestMapping("/dim3d")
@Tag(name = "3D资源管理")
public class Dim3dResourceController {
    @Resource private Dim3dResourceCatalogService service;
    @Resource private Dim3dSceneService sceneService;
    @Resource private ModelImportService modelImports;
    @Resource private ObjectMapper json;
    @Resource private Validator validator;

    @PostMapping("/model/add") @Operation(summary = "新增模型") @PreAuthorize("@ss.hasPermission('dim3d:model:add')")
    public CommonResult<?> addModel(@RequestBody JsonNode body) {
        if (body == null || !body.isObject()) throw new cn.iocoder.wx.framework.common.exception.ServiceException(400, "模型新增请求格式无效");
        // 受管上传必须经过原发布操作，金额保留原始JSON类型，避免小数被实体整数截断。
        if (body.has("operationId") || body.has("resourceId")) {
            return CommonResult.success(modelImports.saveUploadedModel(body));
        }
        // 历史文件编号调用继续沿用实体校验及原目录写入，复用注入的校验器。
        Dim3dModel legacy = json.convertValue(body, Dim3dModel.class);
        ValidationUtils.validate(validator, legacy);
        return CommonResult.success(service.createModel(legacy));
    }
    @PutMapping("/model/edit/{id}") @Operation(summary = "编辑模型") @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Boolean> editModel(@PathVariable Long id, @Valid @RequestBody Dim3dModel body) { service.updateModel(id, body); return CommonResult.success(true); }
    @GetMapping("/model/info/{id}") @Operation(summary = "查询模型详情") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<Dim3dModel> modelInfo(@PathVariable Long id) { return CommonResult.success(service.getModel(id)); }
    @GetMapping("/model/page") @Operation(summary = "分页查询模型") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<PageResult<Dim3dModel>> modelPage(String modelName, String resourceBelong, String status, String isPaid, String category, Integer pageNum, Integer pageSize) { return CommonResult.success(service.getModelPage(modelName, resourceBelong, status, isPaid, category, pageNum, pageSize)); }
    @PutMapping("/model/{status}/{id}") @Operation(summary = "模型上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Boolean> modelStatus(@PathVariable String status, @PathVariable Long id) { service.changeModelStatus(id, status); return CommonResult.success(true); }
    @DeleteMapping("/model/remove/{id}") @Operation(summary = "删除模型") @PreAuthorize("@ss.hasPermission('dim3d:model:delete')")
    public CommonResult<Boolean> removeModel(@PathVariable Long id) { service.deleteModel(id); return CommonResult.success(true); }

    @PostMapping("/icon/add") @Operation(summary = "新增图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:add')")
    // 新图片编号可能超过浏览器安全整数范围，新增回执也按字符串投影。
    public CommonResult<String> addIcon(@Valid @RequestBody Dim3dIcon body) { return CommonResult.success(String.valueOf(service.createIcon(body))); }
    @PutMapping("/icon/edit/{id}") @Operation(summary = "编辑图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:edit')")
    public CommonResult<Boolean> editIcon(@PathVariable Long id, @Valid @RequestBody Dim3dIcon body) { service.updateIcon(id, body); return CommonResult.success(true); }
    @GetMapping("/icon/info/{id}") @Operation(summary = "查询图标详情") @PreAuthorize("@ss.hasPermission('dim3d:icon:query')")
    public CommonResult<Dim3dIcon> iconInfo(@PathVariable Long id) { return CommonResult.success(service.getIcon(id)); }
    @GetMapping("/icon/page") @Operation(summary = "分页查询图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:query')")
    public CommonResult<PageResult<Dim3dIcon>> iconPage(String iconName, String category, String secondaryCategory, String resourceBelong, String status, Integer pageNum, Integer pageSize) { return CommonResult.success(service.getIconPage(iconName, category, secondaryCategory, resourceBelong, status, pageNum, pageSize)); }
    @PutMapping("/icon/{status}/{id}") @Operation(summary = "图标上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:icon:edit')")
    public CommonResult<Boolean> iconStatus(@PathVariable String status, @PathVariable Long id) { service.changeIconStatus(id, status); return CommonResult.success(true); }
    @DeleteMapping("/icon/remove/{id}") @Operation(summary = "删除图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:delete')")
    public CommonResult<Boolean> removeIcon(@PathVariable Long id) { service.deleteIcon(id); return CommonResult.success(true); }

    @PostMapping("/template/add") @Operation(summary = "新增模板") @PreAuthorize("@ss.hasPermission('dim3d:template:add')")
    public CommonResult<Long> addTemplate(@Valid @RequestBody Dim3dTemplate body) { return CommonResult.success(service.createTemplate(body)); }
    @PutMapping("/template/edit/{id}") @Operation(summary = "编辑模板") @PreAuthorize("@ss.hasPermission('dim3d:template:edit')")
    public CommonResult<Boolean> editTemplate(@PathVariable Long id, @Valid @RequestBody Dim3dTemplate body) { service.updateTemplate(id, body); return CommonResult.success(true); }
    @GetMapping("/template/info/{id}") @Operation(summary = "查询模板详情") @PreAuthorize("@ss.hasPermission('dim3d:template:query')")
    public CommonResult<Dim3dTemplate> templateInfo(@PathVariable Long id) { return CommonResult.success(service.getTemplate(id)); }
    @GetMapping("/template/page") @Operation(summary = "分页查询模板") @PreAuthorize("@ss.hasPermission('dim3d:template:query')")
    public CommonResult<?> templatePage(String templateName, String templateType, String category, String resourceBelong,
                                        String status, Integer pageNum, Integer pageSize,
                                        @RequestParam(defaultValue = "false") boolean includeSnapshots) {
        // 只有管理页显式请求合并目录，既有消费者继续沿用原分页及权限过滤。
        if (includeSnapshots)
            return CommonResult.success(sceneService.templatePageCombined(templateName, templateType, category, resourceBelong, status, pageNum, pageSize));
        return CommonResult.success(service.getTemplatePage(templateName, templateType, category, resourceBelong, status, pageNum, pageSize));
    }
    @PutMapping("/template/{status}/{id}") @Operation(summary = "模板上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:template:edit')")
    public CommonResult<Boolean> templateStatus(@PathVariable String status, @PathVariable Long id) { service.changeTemplateStatus(id, status); return CommonResult.success(true); }
    @DeleteMapping("/template/remove/{id}") @Operation(summary = "删除模板") @PreAuthorize("@ss.hasPermission('dim3d:template:delete')")
    public CommonResult<Boolean> removeTemplate(@PathVariable Long id) { service.deleteTemplate(id); return CommonResult.success(true); }

    /** 接收单个ZIP。归属参数启用受管上传，历史调用继续兼容旧回执。 */
    @PostMapping(value = "/resource/uploadConvert", consumes = "multipart/form-data")
    @Operation(summary = "上传ZIP并转换资源")
    // 转换会上传文件并写入资源表，权限必须与模型新增保持一致。
    @PreAuthorize("@ss.hasPermission('dim3d:model:add')")
    public CommonResult<?> uploadConvert(@RequestPart("file") MultipartFile file,
                                                              @RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @RequestHeader(value = "ClientId", required = false) String clientId,
                                                              @RequestParam(value = "resourceBelong", required = false) String resourceBelong) {
        if (resourceBelong != null) return CommonResult.success(modelImports.upload(file, resourceBelong));
        return CommonResult.success(service.convertAndSave(file, authorization, clientId));
    }
}
