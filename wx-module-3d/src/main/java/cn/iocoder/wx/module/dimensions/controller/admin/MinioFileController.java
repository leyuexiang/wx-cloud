package cn.iocoder.wx.module.dimensions.controller.admin;


import cn.hutool.core.util.CharsetUtil;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.ListResult;
import cn.iocoder.wx.module.dimensions.domain.dto.FileInfoBase;
import cn.iocoder.wx.module.dimensions.domain.model.FileUploadInfo;
import cn.iocoder.wx.module.dimensions.domain.model.UploadUrlsVO;
import cn.iocoder.wx.module.dimensions.domain.vo.MinioFileVO;
import cn.iocoder.wx.module.dimensions.entity.FilesMinio;
import cn.iocoder.wx.module.dimensions.service.IFilesMinioService;
import cn.iocoder.wx.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.wx.module.dimensions.utils.FileUtils;
import cn.iocoder.wx.module.dimensions.utils.MinioUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectResponse;
import io.minio.messages.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 9tyf
 * @desc minio文件一系列操作
 */
@Tag(name = "Minio:文件操作接口")
@RestController
@RequestMapping(value = "/dimensions/minio/file")
@Slf4j
public class MinioFileController {

    @Autowired
    private MinioUtil minioUtil;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Resource
    private IFilesMinioService filesMinioService;

    /**
     * 检查文件是否存在
     */
    @Operation(summary = "检查文件是否存在")
    @GetMapping("/multipart/check/{md5}")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:multipartCheck')")
    public CommonResult<FileUploadInfo> checkFileByMd5(@PathVariable String md5) {
        log.info("查询 <{}> 文件是否存在、是否进行断点续传", md5);
        return CommonResult.success(filesMinioService.checkFileByMd5(md5));
    }

    /**
     * 初始化文件分片地址及相关数据
     */
    @Operation(summary = "初始化文件分片地址及相关数据")
    @PostMapping("/multipart/init")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:multipartInit')")
    public CommonResult<UploadUrlsVO> initMultiPartUpload(@RequestBody FileUploadInfo fileUploadInfo) {
        log.info("通过 <{}> 初始化上传任务", fileUploadInfo);
        return CommonResult.success(filesMinioService.initMultipartUpload(fileUploadInfo));
    }

    /**
     * 文件合并（单文件不会合并，仅信息入库）
     */
    @Operation(summary = "文件合并（单文件不会合并，仅信息入库）")
    @PostMapping("/multipart/merge/{md5}")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:multipartMerge')")
    public CommonResult<String> mergeMultipartUpload(@PathVariable String md5) {
        log.info("通过 <{}> 合并上传任务", md5);
        return CommonResult.success(filesMinioService.mergeMultipartUpload(md5));
    }

    /**
     * 下载文件（分片）
     */
    @Operation(summary = "下载文件（分片）")
    @GetMapping("/download/{id}")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:multipartDownload')")
    public ResponseEntity<byte[]> downloadMultipartFile(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.info("通过 <{}> 开始分片下载", id);
        return filesMinioService.downloadMultipartFile(id, request, response);
    }

    @Operation(summary = "获取数据库全部文件列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:multipartList')")
    public ListResult<FilesMinio> getFileList() {
        return ListResult.success(filesMinioService.getFileList());
    }


    @Operation(summary = "上传一个文件")
    @RequestMapping(value = "/uploadFile/{object}", method = RequestMethod.POST, consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasAnyPermissions('dimensions:minio:uploadFile')")
    public CommonResult<String> uploadFile(@RequestPart("uploadFile") MultipartFile uploadFile, @PathVariable String object) throws Exception {
        String name = FileUtils.getFileName(uploadFile.getOriginalFilename());
        String suffix = FileUtils.getFileSuffixLowerCase(uploadFile.getOriginalFilename());
        //判断文件名
        if (StringUtils.isEmpty(name)) {
            return CommonResult.error(500, "文件名错误");
        }

        if (object == null) {
            return CommonResult.error(500, "请定义目录名称");
        }

        if (object.equals("icon")) {
            // 判断资源类型
            if (!FileSuffixConstants.PNG.equals(suffix) && !FileSuffixConstants.WEBP.equals(suffix)) {
                return CommonResult.error(500, "文件类型错误,请上传png、webp类型的文件");
            }
        }
        String fileFullName = name + "_" +  IdWorker.getId() + "." + suffix;
        Path path = Path.of("v1.0.0", object, fileFullName);

        ObjectWriteResponse objectWriteResponse = minioUtil.uploadFile(bucketName, path.toString(), uploadFile.getInputStream());
        return CommonResult.success(objectWriteResponse.object()).setMsg("上传成功");
    }

    @Operation(summary = "列出所有的桶")
    @RequestMapping(value = "/listBuckets", method = RequestMethod.GET)
    @PreAuthorize("@ss.hasPermission('dimensions:minio:listBuckets')")
    public ListResult<String> listBuckets() throws Exception {
        return ListResult.success(minioUtil.getAllBuckets().stream().map(Bucket::name).collect(Collectors.toList()));
    }

    @Operation(summary = "递归列出一个桶中的所有文件和目录")
    @RequestMapping(value = "/listFiles", method = RequestMethod.GET)
    @PreAuthorize("@ss.hasPermission('dimensions:minio:listFiles')")
    public ListResult<FileInfoBase> listFiles(@RequestParam String bucket) {
        return ListResult.success(minioUtil.listFiles(bucket));
    }

    @Operation(summary = "下载一个文件")
    @RequestMapping(value = "/downloadFile", method = RequestMethod.GET)
    @PreAuthorize("@ss.hasPermission('dimensions:minio:downloadFile')")
    public void downloadFile(@RequestParam String bucket, @RequestParam String objectName,
                             HttpServletResponse response) throws Exception {
        InputStream stream = minioUtil.download(bucket, objectName);
        ServletOutputStream output = response.getOutputStream();
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(objectName.substring(objectName.lastIndexOf("/") + 1), StandardCharsets.UTF_8));
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding(CharsetUtil.UTF_8);
        IOUtils.copy(stream, output);
    }

    @Operation(summary = "批量下载文件")
    @PostMapping(value = "/batchDownloadFile")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:batchDownloadFile')")
    public void batchDownloadFile(@RequestBody List<MinioFileVO> minioFileVOList, HttpServletResponse response)
            throws Exception {
        minioUtil.downloadZipFile(minioFileVOList, response);
    }

    @Operation(summary = "删除一个文件")
    @RequestMapping(value = "/deleteFile", method = RequestMethod.GET)
    @PreAuthorize("@ss.hasPermission('dimensions:minio:deleteFile')")
    public CommonResult<String> deleteFile(@RequestParam String bucket, @RequestParam String objectName) throws Exception {
        minioUtil.deleteObject(bucket, objectName);
        return CommonResult.success("删除文件成功");
    }

    @Operation(summary = "删除一个桶")
    @RequestMapping(value = "/deleteBucket", method = RequestMethod.GET)
    @PreAuthorize("@ss.hasPermission('dimensions:minio:deleteBucket')")
    public CommonResult<String> deleteBucket(@RequestParam String bucket) throws Exception {
        minioUtil.deleteBucket(bucket);
        return CommonResult.success("删除桶成功");
    }


    @Operation(summary = "复制一个文件")
    @GetMapping("/copyObject")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:copyObject')")
    public CommonResult<String> copyObject(@RequestParam String sourceBucket, @RequestParam String sourceObject, @RequestParam String targetBucket, @RequestParam String targetObject) throws Exception {
        minioUtil.copyObject(sourceBucket, sourceObject, targetBucket, targetObject);
        return CommonResult.success("复制文件成功");
    }

    @GetMapping("/getObjectInfo")
    @Operation(summary = "获取文件信息")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:getObjectInfo')")
    public CommonResult<StatObjectResponse> getObjectInfo(@RequestParam String bucket, @RequestParam String objectName) throws Exception {
        return CommonResult.success(minioUtil.getObjectInfo(bucket, objectName));
    }

    @GetMapping("/getPresignedObjectUrl")
    @Operation(summary = "获取一个连接以供下载")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:getPresignedObjectUrl')")
    public CommonResult<String> getPresignedObjectUrl(@RequestParam String bucket, @RequestParam String objectName, @RequestParam Integer expires) throws Exception {
        return CommonResult.success(minioUtil.getPresignedObjectUrl(bucket, objectName, expires));
    }

    @GetMapping("/listAllFile")
    @Operation(summary = "获取minio中所有的文件")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:listAllFile')")
    public ListResult<FileInfoBase> listAllFile() throws Exception {
        return ListResult.success(minioUtil.listAllFile());
    }

    @GetMapping("/listFilesInDirectory")
    @Operation(summary = "列出指定目录下的所有文件")
    @PreAuthorize("@ss.hasPermission('dimensions:minio:listFilesInDirectory')")
    public ListResult<String> listFilesInDirectory(@RequestParam String bucket, @RequestParam String directoryPath, boolean recursive) throws Exception {
        return ListResult.success(minioUtil.listFilesInDirectory(bucket, directoryPath, recursive));
    }


}
