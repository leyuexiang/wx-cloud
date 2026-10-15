package cn.iocoder.wx.module.dimensions.controller.admin;


import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.vo.FileInfoUpdateNameVO;
import cn.iocoder.wx.module.dimensions.domain.vo.projectResourceU3DVO;
import cn.iocoder.wx.module.dimensions.entity.FileInfo;
import cn.iocoder.wx.module.dimensions.entity.ResourceInfo;
import cn.iocoder.wx.module.dimensions.service.FileInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/dimensions/file-info")
@Slf4j
@Tag(name = "改造：文件信息接口")
public class FileInfoController extends BaseCrudController<FileInfo, Long> {

    @Value("${server-path}")
    private String serverPath;

    @jakarta.annotation.Resource
    private FileInfoService fileInfoService;

    @GetMapping("/save-file-path")
    @Operation(summary = "保存资源列表")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:saveInfo')")
    public CommonResult<String> saveFileInfo(String projectId) throws IOException {
        Integer save = fileInfoService.saveFileInfo(projectId);
        if (save == 1) {
            return CommonResult.success("更新文件路径成功");
        } else if (save == 2) {
            return CommonResult.error(500,"文件路径获取失败");
        } else {
            return CommonResult.error(500,"获取不到文件下的子文件夹");
        }
    }

    @GetMapping("/get-file-info-by-parent-id")
    @Operation(summary = "根据父ID获取文件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:getByParentId')")
    public CommonResult<List<FileInfo>> saveFileInfo(@RequestParam Integer parentId) {
        FileInfo szl3dFileInfo = new FileInfo();
        szl3dFileInfo.setParentId(parentId == null ? 0 : parentId);
        return CommonResult.success(fileInfoService.selectResourceListByParentId(szl3dFileInfo));
    }

    @PostMapping("/uploads")
    @Operation(summary = "U3D上传资源文件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:upload')")
    public CommonResult<String> upload(MultipartFile[] uploadFiles, projectResourceU3DVO projectResourceU3DVo, HttpServletRequest req) throws IOException {
        return fileInfoService.uploadThreeDimension(uploadFiles, projectResourceU3DVo, req);
    }


    @PostMapping(value = "/upload-banner", consumes = "multipart/form-data")
    @Operation(summary = "前端上传展示图")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:uploadBanner')")
    public CommonResult<?> webUploadBanner(@RequestPart("uploadFiles") MultipartFile[] uploadFiles) {
        return fileInfoService.webUploadBanner(uploadFiles);
    }

    @PostMapping(value = "/uploads-web", consumes = "multipart/form-data")
    @Operation(summary = "前端上传资源文件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:uploadToWeb')")
    public CommonResult<?> uploadToWeb(@RequestPart("uploadFiles") MultipartFile[] uploadFiles) {
        return fileInfoService.uploadWeb(uploadFiles);
    }

    @PostMapping("/uploads-save-web")
    @Operation(summary = "前端保存&修改&更新资源文件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:uploadSaveUpdate')")
    public CommonResult<?> uploadSaveUpdate(@RequestParam String filePath, @RequestBody ResourceInfo resourceInfo) throws IOException {
        return fileInfoService.uploadSaveUpdate(filePath, resourceInfo);
    }

    @DeleteMapping("/delete-file-web")
    @Operation(summary = "前端删除资源")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:deleteFile')")
    public CommonResult<String> deleteFileWeb(@RequestBody ResourceInfo resourceInfo) {
        return CommonResult.success(fileInfoService.deleteFileWeb(resourceInfo));
    }

    @DeleteMapping("/batch_delete-file-web")
    @Operation(summary = "前端批量删除资源")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:batchDeleteFile')")
    public CommonResult<List<Map<ResourceInfo,String>>> batchDeleteFileWeb(@RequestBody List<ResourceInfo> resourceInfos) {
        List<Map<ResourceInfo,String>> list = new ArrayList<>();
        resourceInfos.forEach(resourceInfo -> {
            String msg = fileInfoService.deleteFileWeb(resourceInfo);
            Map<ResourceInfo,String> map = Map.of(resourceInfo, msg);
            list.add(map);
        });

        return CommonResult.success(list);
    }

    @GetMapping("/download")
    @Operation(summary = "下载资源文件(3D接口)")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:down')")
    public void down1(@RequestParam String uploadPath, HttpServletResponse response) {
        fileInfoService.downResource(uploadPath, response);

    }

    @GetMapping("/download-component")
    @Operation(summary = "下载组件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:downComponent')")
    public void down2(@RequestParam String uploadPath, HttpServletResponse response) throws Exception {
        fileInfoService.down(uploadPath, response);
    }

    @GetMapping("/download-resource")
    @Operation(summary = "预览资源文件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:preview')")
    public void preview(@RequestParam String uploadPath, HttpServletResponse response) throws UnsupportedEncodingException {
        fileInfoService.preview(uploadPath, response);
    }

    @GetMapping("/get-img")
    @Operation(summary = "获取图片")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:getImg')")
    public void getImg(@RequestParam String uploadPath, HttpServletResponse response) throws Exception {
        fileInfoService.down(uploadPath, response);
    }

    @PostMapping("/web_download")
    @Operation(summary = "资源导出和下载功能统一接口")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:webDown')")
    public void webDown(@RequestBody int[] ids, HttpServletResponse response) throws IOException {
        fileInfoService.webDownMinio(ids, response);
    }

    @GetMapping("/remove")
    @Operation(summary = "删除资源文件")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:remove')")
    public CommonResult<String> remove(@RequestParam String fileName, @RequestParam Integer parentId, @RequestParam Integer id) {
        Integer integer = fileInfoService.deleteFile(parentId, fileName, id);
        if (integer == 1) {
            return CommonResult.success("删除成功");
        } else {
            return CommonResult.success("删除失败,请联系管理员!");
        }
    }

    @PostMapping("/update_file_name")
    @Operation(summary = "修改文件名")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:updateFileName')")
    public CommonResult<Boolean> updateFileName(@RequestBody FileInfoUpdateNameVO fileInfoUpdateNameVo) {
        return CommonResult.success(fileInfoService.updateFileName(fileInfoUpdateNameVo));
    }

    @PostMapping(value = "/doPostUpload", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:doPostUpload')")
    public HttpEntity doPostUpload(@RequestPart("uploadFiles") MultipartFile uploadFiles, String url) throws IOException {
        return fileInfoService.doPostUpload(uploadFiles, url);
    }

    @GetMapping("/updateResource")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:updateResource')")
    public HttpEntity updateResource() throws IOException {
        String url = serverPath + "/fileserver/savedescfile";
        return fileInfoService.updateResource(url);
    }

    @Operation(summary = "创建文件夹")
    @GetMapping("/create")
    @PreAuthorize("@ss.hasPermission('dimensions:fileInfo:create')")
    public CommonResult<String> create(@RequestParam String fileName) {
        fileInfoService.create(fileName);
        return CommonResult.success("创建成功");
    }
}