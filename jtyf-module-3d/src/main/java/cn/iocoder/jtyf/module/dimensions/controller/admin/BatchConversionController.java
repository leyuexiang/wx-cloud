package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.ListResult;
import cn.iocoder.jtyf.module.dimensions.entity.BatchConversion;
import cn.iocoder.jtyf.module.dimensions.service.BatchConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/dimensions/batch-conversion")
@Tag(name = "批量转换功能")
public class BatchConversionController {

    @Resource
    BatchConversionService batchConversionService;

    @PostMapping(value = "/upload-to-web", consumes = "multipart/form-data")
    @Operation(summary = "上传压缩包到页面")
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:upload')")
    public CommonResult<BatchConversion> uploadToWeb(@RequestPart("uploadFile") MultipartFile uploadFile) {
        return CommonResult.success(batchConversionService.uploadToWeb(uploadFile));
    }

    @PostMapping("/to-gltf/{id}")
    @Operation(summary = "obj/Fbx/3ds转gltf")
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:toGltf')")
    public CommonResult<?> toGltf(@PathVariable(value = "id") Integer id) throws IOException {
       return batchConversionService.toGltf(id);
    }

    @GetMapping("/delete-all")
    @Operation(summary = "清空当前用户的缓存文件夹及表数据")
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:delete')")
    public CommonResult<String> deleteAll()  {
        batchConversionService.deleteAll();
        return CommonResult.success("成功清空缓存文件夹及表数据");
    }

    @PostMapping("/download-gltf")
    @Operation(summary = "批量下载转换文件")
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:download')")
    public void downloadGltf(@RequestBody int[] ids, HttpServletResponse response)  {
         batchConversionService.downloadGltf(ids,response);
    }

    @GetMapping("/get-list")
    @Operation(summary = "获取转换列表")
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:list')")
    public ListResult<BatchConversion> getList()  {
        return ListResult.success(batchConversionService.list());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除资源")
    @Parameter(name = "id", description = "主键", required = true)
    @PreAuthorize("@ss.hasPermission('dimensions:batchConversion:deleteSource')")
    public CommonResult<Boolean> deleteSource(@RequestParam("id") Long id) {
        return CommonResult.success(batchConversionService.deleteById(id));
    }

}
