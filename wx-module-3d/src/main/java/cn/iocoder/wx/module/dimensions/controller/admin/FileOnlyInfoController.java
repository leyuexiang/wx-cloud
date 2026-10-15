package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.entity.FileOnlyInfo;
import cn.iocoder.wx.module.dimensions.service.FileOnlyInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dimensions/file-only-info")
@Slf4j
@Tag(name = "文件唯一信息表接口")
public class FileOnlyInfoController extends BaseCrudController<FileOnlyInfo, String> {

    @Resource
    private FileOnlyInfoService fileOnlyInfoService;

    @GetMapping("/get-by-projectId")
    @Operation(summary = "根据id获取项目信息")
    @PreAuthorize("@ss.hasPermission('dimensions:fileOnly:id')")
    public ListResult<String> getByIds(String projectId){
        return ListResult.success(fileOnlyInfoService.getFileOnlyInfo(projectId));
    }
}
