package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.validation.ValidationUtils;
import cn.iocoder.wx.module.dimensions.service.ModelImportService;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dIconForm;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dIconPageResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dIconResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelPageResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dTemplateForm;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dTemplatePageResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dTemplateResponse;
import cn.iocoder.wx.module.dimensions.service.Dim3dCatalogResponseAssembler;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.service.Dim3dResourceCatalogService;
import cn.iocoder.wx.module.dimensions.service.Dim3dSceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.wx.module.dimensions.config.ModelPlatformConfiguration.PLATFORM_SEGMENT;


/**
 * 新资源管理接口。
 * 模型提供两端平台路径和旧管理路径，多个入口复用同一表单、响应及受控业务。
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
    /** 目录响应使用明确字段投影，操作人名称按一页去重合批补充。 */
    @Resource private Dim3dCatalogResponseAssembler catalogResponses;

    @PostMapping({"/model/add", PLATFORM_SEGMENT + "/model/add"})
    @Operation(summary = "新增模型", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = @Content(schema = @Schema(implementation = Dim3dModel.class))))
    @PreAuthorize("@ss.hasPermission('dim3d:model:add')")
    public CommonResult<?> addModel(@RequestBody JsonNode body) {
        // 已部署网页仍按原操作保存并轮询回执。仅该明确旧形态复用原服务，完整文档表单仍返回空数据。
        if (body != null && body.isObject() && body.has("operationId") && body.has("resourceId") && !body.has("modelFileId")) {
            return CommonResult.success(modelImports.saveUploadedModel(body));
        }
        // 目录新增统一接收文档表单，内部资源发布完成后才返回空成功响应。
        service.createModel(modelInput(body));
        return CommonResult.success(null);
    }
    @PutMapping({"/model/edit/{id}", PLATFORM_SEGMENT + "/model/edit/{id}"})
    // 原始节点用于严格金额校验，接口文档继续展示原模型字段及必填约束。
    @Operation(summary = "编辑模型", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = @Content(schema = @Schema(implementation = Dim3dModel.class))))
    @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Void> editModel(@PathVariable Long id, @RequestBody JsonNode body,
                                        @RequestParam(required = false) String operationId) {
        // 新增与编辑共用原始金额检查和实体校验，确保编辑也不会截断小数金额。
        var model = modelInput(body);
        if (operationId == null) service.updateModel(id, model);
        else service.replaceManagedModel(id, model, operationId);
        return CommonResult.success(null);
    }
    @GetMapping({"/model/info/{id}", PLATFORM_SEGMENT + "/model/info/{id}"}) @Operation(summary = "查询模型详情") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<Dim3dModelResponse> modelInfo(@PathVariable Long id) {
        return CommonResult.success(catalogResponses.single(service.getModel(id)));
    }
    @GetMapping({"/model/page", PLATFORM_SEGMENT + "/model/page"}) @Operation(summary = "分页查询模型") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public Dim3dModelPageResponse modelPage(String modelName, String resourceBelong, String status, String isPaid,
                                            String category, Integer pageNum, Integer pageSize,
                                            String orderByColumn, String isAsc) {
        // 模型分页直接返回契约根级字段，避免重复序列化同一记录集合。
        var page = service.getModelPage(modelName, resourceBelong, status, isPaid,
                category, pageNum, pageSize, orderByColumn, isAsc);
        return new Dim3dModelPageResponse(new PageResult<>(catalogResponses.multiple(page.getList()), page.getTotal()));
    }
    /** 上下线沿用文档路径和模型编辑权限，成功仅返回统一空数据响应。 */
    @PutMapping({"/model/{status}/{id}", PLATFORM_SEGMENT + "/model/{status}/{id}"}) @Operation(summary = "模型上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Void> modelStatus(@PathVariable String status, @PathVariable Long id) { service.changeModelStatus(id, status); return CommonResult.success(null); }
    @DeleteMapping({"/model/remove/{id}", PLATFORM_SEGMENT + "/model/remove/{id}"}) @Operation(summary = "删除模型") @PreAuthorize("@ss.hasPermission('dim3d:model:delete')")
    public CommonResult<Void> removeModel(@PathVariable Long id) { service.deleteModel(id); return CommonResult.success(null); }

    /** 文件详情按模型文件编号及目录可见范围读取，仅返回文档约定的相对路径。 */
    @GetMapping({"/resource/info/{id}", PLATFORM_SEGMENT + "/resource/info/{id}"})
    @Operation(summary = "查询模型文件资源详情")
    @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<?> modelResourceInfo(@PathVariable Long id) {
        return CommonResult.success(service.getModelResource(id));
    }

    /** 轻量读取文件访问方式，受管模型由播放器依据稳定编号执行原受控下载。 */
    @GetMapping({"/model/file-access/{id}", PLATFORM_SEGMENT + "/model/file-access/{id}"})
    @Operation(summary = "查询模型文件访问方式")
    @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<?> modelFileAccess(@PathVariable Long id) {
        return CommonResult.success(service.getModelFileAccess(id));
    }

    /**
     * 模型表单先检查整数分再转换，避免默认数字强制转换造成金额截断或溢出。
     * 资源归属、文件关联和受控发布由统一模型业务服务判断，客户端审计字段不参与决策。
     */
    private Dim3dModel modelInput(JsonNode body) {
        if (body == null || !body.isObject()) throw new ServiceException(400, "模型请求格式无效");
        JsonNode price = body.get("price");
        if (price != null && !price.isNull() && (!price.isIntegralNumber() || !price.canConvertToInt() || price.asInt() < 0)) {
            throw new ServiceException(400, "模型价格必须是有效的整数分");
        }
        Dim3dModel model;
        try {
            model = json.convertValue(body, Dim3dModel.class);
        } catch (IllegalArgumentException invalid) {
            throw new ServiceException(400, "模型字段格式无效");
        }
        ValidationUtils.validate(validator, model);
        return model;
    }

    /** 目录和文件绑定提交成功后，按图片上传合同返回字符串编号。 */
    @PostMapping("/icon/add") @Operation(summary = "新增图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:add')")
    public CommonResult<String> addIcon(@Valid @RequestBody Dim3dIconForm body) {
        return CommonResult.success(String.valueOf(service.createIcon(body.toIcon())));
    }
    @PutMapping("/icon/edit/{id}") @Operation(summary = "编辑图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:edit')")
    public CommonResult<Void> editIcon(@PathVariable Long id, @Valid @RequestBody Dim3dIconForm body) {
        service.updateIcon(id, body.toIcon());
        return CommonResult.success(null);
    }
    @GetMapping("/icon/info/{id}") @Operation(summary = "查询图标详情") @PreAuthorize("@ss.hasPermission('dim3d:icon:query')")
    public CommonResult<Dim3dIconResponse> iconInfo(@PathVariable Long id) {
        // 目录查询按现有可见范围授权，响应只使用业务投影。
        return CommonResult.success(catalogResponses.icon(service.getIcon(id)));
    }
    @GetMapping("/icon/page") @Operation(summary = "分页查询图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:query')")
    public Dim3dIconPageResponse iconPage(String iconName, String category, String secondaryCategory,
                                         String resourceBelong, String status, Integer pageNum, Integer pageSize,
                                         String orderByColumn, String isAsc) {
        // 使用根级分页，所有筛选和排序在数据库完成，操作人信息按当前页批量补齐。
        var page = service.getIconPage(iconName, category, secondaryCategory, resourceBelong, status, pageNum,
                pageSize, orderByColumn, isAsc);
        return new Dim3dIconPageResponse(page.getTotal(), catalogResponses.icons(page.getList()));
    }
    @PutMapping("/icon/{status}/{id}") @Operation(summary = "图标上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:icon:edit')")
    public CommonResult<Void> iconStatus(@PathVariable String status, @PathVariable Long id) {
        service.changeIconStatus(id, status);
        return CommonResult.success(null);
    }
    @DeleteMapping("/icon/remove/{id}") @Operation(summary = "删除图标") @PreAuthorize("@ss.hasPermission('dim3d:icon:delete')")
    public CommonResult<Void> removeIcon(@PathVariable Long id) {
        service.deleteIcon(id);
        return CommonResult.success(null);
    }

    @PostMapping("/template/add") @Operation(summary = "新增模板") @PreAuthorize("@ss.hasPermission('dim3d:template:add')")
    public CommonResult<Void> addTemplate(@Valid @RequestBody Dim3dTemplateForm body) {
        service.createTemplate(body.toTemplate());
        return CommonResult.success(null);
    }
    @PutMapping("/template/edit/{id}") @Operation(summary = "编辑模板") @PreAuthorize("@ss.hasPermission('dim3d:template:edit')")
    public CommonResult<Void> editTemplate(@PathVariable Long id, @Valid @RequestBody Dim3dTemplateForm body) {
        service.updateTemplate(id, body.toTemplate());
        return CommonResult.success(null);
    }
    @GetMapping("/template/info/{id}") @Operation(summary = "查询模板详情") @PreAuthorize("@ss.hasPermission('dim3d:template:query')")
    public CommonResult<Dim3dTemplateResponse> templateInfo(@PathVariable Long id) {
        return CommonResult.success(catalogResponses.template(service.getTemplate(id)));
    }
    @GetMapping("/template/page") @Operation(summary = "分页查询模板") @PreAuthorize("@ss.hasPermission('dim3d:template:query')")
    public Dim3dTemplatePageResponse<?> templatePage(String templateName, String templateType, String category,
                                                     String resourceBelong, String status, Integer pageNum, Integer pageSize,
                                                     String orderByColumn, String isAsc,
                                                     @RequestParam(defaultValue = "false") boolean includeSnapshots) {
        // 管理页显式请求合并时保留记录来源，编辑及治理可以定位各自的业务目录。
        if (includeSnapshots) {
            var page = sceneService.templatePageCombined(templateName, templateType, category, resourceBelong,
                    status, pageNum, pageSize, orderByColumn, isAsc);
            return new Dim3dTemplatePageResponse<>(page.getTotal(), page.getList());
        }
        var page = service.getTemplatePage(templateName, templateType, category, resourceBelong,
                status, pageNum, pageSize, orderByColumn, isAsc);
        return new Dim3dTemplatePageResponse<>(page.getTotal(), catalogResponses.templates(page.getList()));
    }
    @PutMapping("/template/{status}/{id}") @Operation(summary = "模板上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:template:edit')")
    public CommonResult<Void> templateStatus(@PathVariable String status, @PathVariable Long id) {
        service.changeTemplateStatus(id, status);
        return CommonResult.success(null);
    }
    @DeleteMapping("/template/remove/{id}") @Operation(summary = "删除模板") @PreAuthorize("@ss.hasPermission('dim3d:template:delete')")
    public CommonResult<Void> removeTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return CommonResult.success(null);
    }

    /** 接收单个ZIP。旧归属参数仍用于选择受管回执，最终归属以目录保存表单为准。 */
    @PostMapping(value = {"/resource/uploadConvert", PLATFORM_SEGMENT + "/resource/uploadConvert"}, consumes = "multipart/form-data")
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
