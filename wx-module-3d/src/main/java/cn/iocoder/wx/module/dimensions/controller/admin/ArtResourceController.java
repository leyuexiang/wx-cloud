package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.ArtResourceService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/** 资源读取及单资源事务接口；所有授权和摘要校验均在服务端执行。 */
@RestController
@RequestMapping("/dim3d")
public class ArtResourceController {
    @Resource private ArtResourceService service;
    @Resource private cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier parser;
    @Resource private cn.iocoder.wx.module.dimensions.service.ArtResourceTransactionService transaction;
    @GetMapping("/resources")
    public CommonResult<?> page(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="20") int pageSize) {
        return CommonResult.success(service.page(pageNum,pageSize));
    }
    @GetMapping("/resources/{resourceId}/current")
    public CommonResult<?> current(@PathVariable String resourceId){return CommonResult.success(service.current(resourceId));}
    @GetMapping("/resources/{resourceId}/file")
    public CommonResult<?> file(@PathVariable String resourceId,@RequestParam String revision,@RequestParam String contentHash,@RequestParam String path){
        return CommonResult.success(service.file(resourceId,revision,contentHash,path));
    }
    @PostMapping("/resource-operations")
    public CommonResult<?> create(@RequestBody byte[] body){return CommonResult.success(service.create(parser.parse(body)));}
    @GetMapping("/resource-operations/{operationId}")
    public CommonResult<?> operation(@PathVariable String operationId){return CommonResult.success(service.operation(operationId));}
    @PostMapping("/resource-operations/{operationId}/cancel")
    public CommonResult<?> cancel(@PathVariable String operationId){return CommonResult.success(service.cancel(operationId));}
    @PostMapping("/resource-operations/{operationId}/upload-targets")
    public CommonResult<?> upload(@PathVariable String operationId,@RequestBody byte[] body){return CommonResult.success(transaction.uploadTargets(operationId,parser.parse(body)));}
    @PostMapping("/resource-operations/{operationId}/commit")
    public CommonResult<?> commit(@PathVariable String operationId,@RequestBody byte[] body){return CommonResult.success(transaction.commit(operationId,parser.parse(body)));}
}
