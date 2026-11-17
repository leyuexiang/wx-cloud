package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.validation.ValidationUtils;
import cn.iocoder.wx.module.dimensions.service.ModelImportService;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelPageResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelResponse;
import cn.iocoder.wx.module.dimensions.service.Dim3dModelResponseAssembler;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
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
    /** 目录响应使用明确字段投影，操作人名称按一页去重合批补充。 */
    @Resource private Dim3dModelResponseAssembler modelResponses;

    @PostMapping("/model/add")
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
    @PutMapping("/model/edit/{id}")
    // 原始节点用于严格金额校验，接口文档继续展示原模型字段及必填约束。
    @Operation(summary = "编辑模型", requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = @Content(schema = @Schema(implementation = Dim3dModel.class))))
    @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Void> editModel(@PathVariable Long id, @RequestBody JsonNode body) {
        // 新增与编辑共用原始金额检查和实体校验，确保编辑也不会截断小数金额。
        service.updateModel(id, modelInput(body));
        return CommonResult.success(null);
    }
    @GetMapping("/model/info/{id}") @Operation(summary = "查询模型详情") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<Dim3dModelResponse> modelInfo(@PathVariable Long id) {
        return CommonResult.success(modelResponses.single(service.getModel(id)));
    }
    @GetMapping("/model/page") @Operation(summary = "分页查询模型") @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public Dim3dModelPageResponse modelPage(String modelName, String resourceBelong, String status, String isPaid,
                                            String category, Integer pageNum, Integer pageSize,
                                            String orderByColumn, String isAsc) {
        // 模型分页直接返回契约根级字段，避免重复序列化同一记录集合。
        var page = service.getModelPage(modelName, resourceBelong, status, isPaid,
                category, pageNum, pageSize, orderByColumn, isAsc);
        return new Dim3dModelPageResponse(new PageResult<>(modelResponses.multiple(page.getList()), page.getTotal()));
    }
    /** 上下线沿用文档路径和模型编辑权限，成功仅返回统一空数据响应。 */
    @PutMapping("/model/{status}/{id}") @Operation(summary = "模型上线或下线") @PreAuthorize("@ss.hasPermission('dim3d:model:edit')")
    public CommonResult<Void> modelStatus(@PathVariable String status, @PathVariable Long id) { service.changeModelStatus(id, status); return CommonResult.success(null); }
    @DeleteMapping("/model/remove/{id}") @Operation(summary = "删除模型") @PreAuthorize("@ss.hasPermission('dim3d:model:delete')")
    public CommonResult<Void> removeModel(@PathVariable Long id) { service.deleteModel(id); return CommonResult.success(null); }

    /** 文件详情复用目录可见范围，补齐文档约定的模型文件编号查询入口。 */
    @GetMapping("/resource/info/{id}")
    @Operation(summary = "查询模型文件资源详情")
    @PreAuthorize("@ss.hasPermission('dim3d:model:query')")
    public CommonResult<?> modelResourceInfo(@PathVariable Long id) {
        return CommonResult.success(service.getModelResource(id));
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
