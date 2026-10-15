package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.FileInfoUpdateNameVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.projectResourceU3DVO;
import cn.iocoder.jtyf.module.dimensions.entity.*;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.FileInfoMapper;
import cn.iocoder.jtyf.module.dimensions.mapper.FileOnlyInfoMapper;
import cn.iocoder.jtyf.module.dimensions.service.FileInfoService;
import cn.iocoder.jtyf.module.dimensions.service.ResourceInfoService;
import cn.iocoder.jtyf.module.dimensions.service.ResourceService;
import cn.iocoder.jtyf.module.dimensions.service.ResourceVersionService;
import cn.iocoder.jtyf.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.jtyf.module.dimensions.service.constant.ModelConvertConstants;
import cn.iocoder.jtyf.module.dimensions.utils.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import io.minio.Result;
import io.minio.errors.*;
import io.minio.messages.Item;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static cn.iocoder.jtyf.module.dimensions.config.UploadJobConfiguration.UPLOAD_THREAD_POOL_TASK_EXECUTOR;

@Service
@Slf4j
public class FileInfoServiceImpl extends ServiceImpl<FileInfoMapper, FileInfo> implements FileInfoService {

    @Value("${file-save-path}")
    private String fileSavePath_;

    @Value("${file-save-temporary-path}")
    private String fileSaveTemporaryPath;

    @Value("${file-save-img-path}")
    private String fileSaveImgPath;

    @Value("${server-path}")
    private String serverPath;

    @Value("${minio.bucketName}")
    private String bucketName;

    @Value("${project-name-prefix}")
    private  String projectNamePrefix;

    @jakarta.annotation.Resource
    private FileOnlyInfoMapper fileOnlyInfoMapper;

    @jakarta.annotation.Resource
    private FileInfoMapper fileInfoMapper;

    @jakarta.annotation.Resource
    private ResourceService resourceService;

    @jakarta.annotation.Resource
    private ResourceInfoService resourceInfoService;

    @jakarta.annotation.Resource
    private ResourceVersionService resourceVersionService;

    @jakarta.annotation.Resource
    private MinioUtil minioUtil;

    @jakarta.annotation.Resource(name = UPLOAD_THREAD_POOL_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;



    @Override
    public void create(String fileName) {
        String dirStr = fileSavePath_ + File.separator + fileName;
        File directory = new File(dirStr);
        if (directory.mkdirs()) {
            log.info("创建文件夹成功");
        }
        log.info("创建文件夹失败");
    }

    @Override
    public HttpEntity updateResource(String url) throws IOException {
        return HttpUtils.updateResource(url);
    }

    @Override
    public HttpEntity doPostUpload(MultipartFile uploadFiles, String url) throws IOException {
        return HttpUtils.uploadFile(uploadFiles, url);
    }

    @Override
    public String getDownloadPath(Integer parentId, int type) {
        StringBuilder path = new StringBuilder();
        List<FileInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getId, parentId));
        if (type == 1) {
            while (!list.isEmpty()) {
                path.insert(0, list.get(0).getFileName() + "/");
                parentId = list.get(0).getParentId();
                list = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getId, parentId));
            }
        } else {
            while (!list.isEmpty() && list.get(0).getParentId() != 0) {
                path.insert(0, list.get(0).getFileName() + "/");
                parentId = list.get(0).getParentId();
                list = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getId, parentId));
            }
        }
        return path.toString();
    }

    @Override
    public Integer deleteFile(Integer parentId, String fileName, Integer id) {
        try {
            StringBuilder path = new StringBuilder();
            List<FileInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getId, parentId));
            while (!list.isEmpty()) {
                path.insert(0, list.get(0).getFileName() + "/");
                Integer parentId1 = list.get(0).getParentId();
                list = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getId, parentId1));
            }
            Path path2 = Paths.get(path + fileName);
            Files.delete(path2);
            this.baseMapper.deleteById(id);
            return 1;
        } catch (IOException e) {
            log.error("删除文件失败：{}", e.getMessage());
            return 0;
        }
    }

//    public String getUploadPath(MultipartFile uploadFile, Integer Id) {
//        List<Szl3dFileInfo> list = selectById(Id);
//        StringBuilder path = new StringBuilder();
//        while (list.size() > 0) {
//            path.insert(0, list.get(0).getFileName() + "/");
//            int ParentId = list.get(0).getParentId();
//            list = selectById(ParentId);
//        }
//        return path.toString();
//    }


//    public String getUploadPathNoSave(MultipartFile uploadFile, Integer Id) {
//        StringBuilder path = new StringBuilder();
//        //PSFramework目录下的文件
//        List<Szl3dFileInfo> list = selectById(Id);
//        while (list.size() > 0) {
//            path.insert(0, list.get(0).getFileName() + "/");
//            int ParentId = list.get(0).getParentId();
//            list = selectById(ParentId);
//        }
//        return path.toString();
//    }


    @Override
    public List<FileInfo> selectResourceListByParentId(FileInfo szl3dFileInfo) {
        return fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getParentId, szl3dFileInfo.getParentId()));
    }

    @Override
    public Integer deleteAndSave(String appId) throws IOException {
        this.baseMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getAppId,Long.valueOf(appId)));
        fileOnlyInfoMapper.delete(new LambdaQueryWrapper<FileOnlyInfo>().eq(FileOnlyInfo::getAppId,Long.valueOf(appId)));
        File file = new File(fileSavePath_);

        log.info("U3D上传资源文件：filePath:{},getAbsolutePath: {}", fileSavePath_, file.getAbsolutePath());

        return saveFilePath(file, 0, 0, "", "0",appId);
    }

    //删除指定单体模型和场景模型
//    public boolean deleteResource(String artResName) {
//        List<Szl3dFileOnlyInfo> list = fileOnlyInfoMapper.selectList(new LambdaQueryWrapper<Szl3dFileOnlyInfo>()
//                .eq(Szl3dFileOnlyInfo::getParentName, artResName));
//        if (list.size() > 0) {
//            String uniqueId = list.get(0).getUniqueId();
//            fileInfoMapper.delete(new LambdaQueryWrapper<Szl3dFileInfo>()
//                    .eq(Szl3dFileInfo::getUniqueId, uniqueId));
//            fileOnlyInfoMapper.delete(new LambdaQueryWrapper<Szl3dFileOnlyInfo>()
//                    .eq(Szl3dFileOnlyInfo::getUniqueId, uniqueId));
//            return true;
//        } else {
//            return false;
//        }
//    }

    @Override
    public void save(String appId) throws IOException {
        saveFilePath(new File(fileSavePath_), 0, 0, "", "0",appId);
    }


    public Integer saveFilePath(File dir, Integer level, Integer parentId, String parentName, String uniqueId,String appId) throws IOException {

        if (dir == null) {
            return 2;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File fileItem : files) {
                if (fileItem.getName().equals("UpdateExe") || fileItem.getName().equals("temporary")) {
                    continue;
                }
                FileInfo szl3dFileInfo = new FileInfo();
                szl3dFileInfo.setFileName(fileItem.getName());
                szl3dFileInfo.setFileLevel(level);
                szl3dFileInfo.setFileSize(String.valueOf(fileItem.length()));
                szl3dFileInfo.setParentId(parentId);
                szl3dFileInfo.setUploadTime(LocalDateTime.now());
                szl3dFileInfo.setUploadUserId(1);
                //TODO appID需要更改
                szl3dFileInfo.setAppId(1L);
                szl3dFileInfo.setUploadUserName(SecurityUtil.getUsername());
                szl3dFileInfo.setStatus(1);
                List<FileInfo> fileDouble = this.baseMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getParentId, szl3dFileInfo.getParentId())
                        .eq(FileInfo::getFileLevel, szl3dFileInfo.getFileLevel())
                        .eq(FileInfo::getFileName, szl3dFileInfo.getFileName())
                );

                //log.info("fileInfo:{}", szl3dFileInfo);
                if (fileItem.isDirectory()) {
                    szl3dFileInfo.setFileType(0);
                    if (fileDouble.size() < 1) {
                        if (level == 1) {
                            uniqueId = UUID.randomUUID().toString();
                            szl3dFileInfo.setUniqueId(uniqueId);
                        } else if (level > 1) {
                            //二级以上目录跟着二级目录一起删,共享一个uniqueId,以PSFramework/作为第一级
                            szl3dFileInfo.setUniqueId(uniqueId);
                        }
                        this.baseMapper.insert(szl3dFileInfo);
                    } else {
                        //数据库中已存在此数据
                        szl3dFileInfo = fileDouble.get(0);
                    }
                    saveFilePath(fileItem, level + 1, szl3dFileInfo.getId(), fileItem.getName(), szl3dFileInfo.getUniqueId(),appId);
                } else {
                    szl3dFileInfo.setFileType(1);
                    //文件表存了文件夹内容，文件夹里面的文件也相应存了;
                    if (fileDouble.size() < 1) {
                        if(level == 1){
                            szl3dFileInfo.setUniqueId(null);
                        }else {
                            szl3dFileInfo.setUniqueId(uniqueId);
                        }
                        this.baseMapper.insert(szl3dFileInfo);
                        FileOnlyInfo fileOnlyInfo = new FileOnlyInfo();
                        fileOnlyInfo.setFileName(fileItem.getName());
                        fileOnlyInfo.setFileCode(MD5Utils.getFileMD5String(fileItem).toLowerCase(Locale.ROOT));
                        fileOnlyInfo.setStatus(1);
                        fileOnlyInfo.setParentId(parentId);
                        fileOnlyInfo.setParentName(parentName);
                        fileOnlyInfo.setProjectId(appId);
                        if(level == 1){
                            szl3dFileInfo.setUniqueId(null);
                        }else {
                            fileOnlyInfo.setUniqueId(uniqueId);
                        }
                        //TODO appID
                        fileOnlyInfo.setAppId(1L);
                        fileOnlyInfoMapper.insert(fileOnlyInfo);
                    }
                }
            }
//
            this.baseMapper.updateAllHasNext();
            this.baseMapper.updateHasNext();
            return 1;
        }
        return 3;
    }

    @Override
    public boolean validateZipContents(String zipFilePath) {
        try (ZipFile zipFile = new ZipFile(zipFilePath, ZipUtils.zipCharset(zipFilePath))) {
            int count = 0;

            // 遍历 ZIP 文件中的所有条目
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String fileSuffix = FileUtils.getFileSuffixLowerCase(entry.getName());
                if (ModelConvertConstants.ENABLE_SUFFIX_SET.contains(fileSuffix)) {
                    count++;
                }
            }
            // 上传或下载时， 可能会有obj与gltf/bin文件同时存在，所以需放开
            return count == 1;
        } catch (IOException e) {
            log.error("压缩包校验obj/fbx/3ds/glb文件失败{}", e.getMessage());
            return false;
        }
    }

    //检查压缩包内子文件及子目录内是否含有目标文件
    @Override
    public boolean checkZipFileContainsEntry(String zipFilePath, String targetFileName) {
        boolean hasFile = false;
        try {
            ZipFile zipFile = new ZipFile(zipFilePath, StandardCharsets.UTF_8);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                //System.out.println("检测配置文件-压缩包内文件名"+entryName);-调试用
                if (!entry.isDirectory()) {
                    if (entryName.substring(entryName.lastIndexOf("/") + 1).equals(targetFileName)) {
                        hasFile = true;
                        break;
                    }
                }
            }
            zipFile.close();
        } catch (IOException e) {
            log.error("压缩包检查配置文件失败：{}", e.getMessage());
            throw new RuntimeException(e);
        }
        return hasFile;
    }

    //获取文件的小写扩展名
    public static String getFileExtension(String fullName) {
        Preconditions.checkNotNull(fullName);
        String fileName = (new File(fullName)).getName();
        int dotIndex = fileName.lastIndexOf(46);
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }


    @Override
    public CommonResult<String> uploadThreeDimension(MultipartFile[] uploadFiles, projectResourceU3DVO projectResourceU3DVo, HttpServletRequest req) throws IOException {
        String filePath = "";
        String uploadPath = projectResourceU3DVo.getUploadPath();
        for (MultipartFile uploadFile : uploadFiles) {
            uploadPath = uploadPath.replaceAll("\\\\", "/");

            File folder = Paths.get(fileSavePath_, uploadPath).toFile();
            if (!folder.isDirectory()) {
                if (folder.mkdirs()) {
                    log.info("解压父目录创建成功");
                } else {
                    return CommonResult.error(500,"解压父目录创建失败");
                }
            }
            String oldName = uploadFile.getOriginalFilename();
            try {
                Path targetDirectory = Paths.get(folder.getPath().replaceAll("\\\\", "/"),oldName);
                assert oldName != null;
                Files.copy(uploadFile.getInputStream(), targetDirectory, StandardCopyOption.REPLACE_EXISTING);
                filePath = req.getScheme() + "://" + req.getServerName() + ":" +
                        req.getServerPort() + "/file-resource/" + uploadPath + oldName;
            } catch (IOException e) {
                log.error("U3D上传资源文件上传失败，{}",e.getMessage());
            }
            String suffix = FileUtils.getFileSuffixLowerCase(uploadFile.getOriginalFilename());
            if (FileSuffixConstants.ZIP.equals(suffix)) {
                Path zipPath = Paths.get(fileSavePath_, uploadPath, uploadFile.getOriginalFilename());
                ZipUtils.unzip(zipPath.toString(), fileSavePath_  + uploadPath);
                Resource resource = new Resource();
                resource.setArtResType(projectResourceU3DVo.getArtResType());
                resource.setAssetBundelName(projectResourceU3DVo.getAssetBundelName());
                resource.setAssetName(projectResourceU3DVo.getAssetName());
                resource.setPreviewOutPath(projectResourceU3DVo.getPreviewOutPath());
                resource.setUniqueId(projectResourceU3DVo.getUniqueId());
                resource.setResFromPlatform(projectResourceU3DVo.getResFromPlatform());
                resource.setProjectId(projectResourceU3DVo.getProjectId());
                resource.setArtResName(projectResourceU3DVo.getArtResName());
                resource.setResourceName(projectResourceU3DVo.getAssetName().split("\\.")[0]);
                resource.setUploadUserName(SecurityUtil.getUsername());
                resourceService.save(resource);
                deleteAndSave(projectResourceU3DVo.getProjectId());
            } else {
                save(projectResourceU3DVo.getProjectId());
            }
        }
        return CommonResult.success(filePath);
    }

    @Override
    public CommonResult<?> webUploadBanner(MultipartFile[] uploadFiles) {
        String filePath = "";
        log.info("uploadFiles:{}", (Object) uploadFiles);
        for (MultipartFile uploadFile : uploadFiles) {
            //解压父目录不存在时
            String oldName = System.currentTimeMillis() + "." + FileUtils.getFileSuffix(uploadFile.getOriginalFilename());
            File folder = new File(fileSaveImgPath);
            Path targetDirectory = Paths.get(folder.getPath().replaceAll("\\\\", "/"),oldName);
            if (!folder.isDirectory()) {
                if (folder.mkdirs()) {
                    log.info("文件临时存储目录创建成功");
                } else {
                    return CommonResult.error(500,"文件临时存储目录创建失败");
                }
            }
            try {
                Files.copy(uploadFile.getInputStream(), targetDirectory, StandardCopyOption.REPLACE_EXISTING);
                filePath = fileSaveImgPath + oldName;
            } catch (IOException e) {
                log.error("前端上传展示图失败：{}", e.getMessage());
            }
        }
        filePath = serverPath + "/file-info/get-img?uploadPath=" + filePath;
        log.info("filePath:{}", filePath);
        return CommonResult.success(filePath);
    }

    @Override
    public void down(String uploadAddressPath, HttpServletResponse response) throws IOException {
        response.reset();
        response.setContentType("image/png;charset=utf-8");
        Path uploadPath = Paths.get(uploadAddressPath);
        response.addHeader("Content-Disposition", "attachment;fileName=" + uploadPath.toFile().getName());// 设置文件名
        try (
                BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(uploadPath));
                // 输出流
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())
        ) {
            byte[] buff = new byte[1024];
            int len;
            while ((len = bis.read(buff)) > 0) {
                bos.write(buff, 0, len);
            }
        }
    }

    @Override
    public void downResource(String uploadAddressPath, HttpServletResponse response) {
        response.reset();
        response.setContentType("application/octet-stream;charset=utf-8");
        Path uploadPath = Paths.get(fileSavePath_, uploadAddressPath);
        response.addHeader("Content-Disposition", "attachment;fileName=" + uploadPath.toFile().getName());// 设置文件名
//        String uploadPath = getFileInfoService().getDownloadPath(parentId,1);
        try (
                BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(uploadPath));
                // 输出流
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())
        ) {
            byte[] buff = new byte[1024];
            int len;
            while ((len = bis.read(buff)) > 0) {
                bos.write(buff, 0, len);
            }
        } catch (IOException e) {
            log.error("下载资源文件(3D接口)失败：{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void preview(String uploadAddressPath, HttpServletResponse response) {
        response.reset();
        if(uploadAddressPath.contains("mp4") || uploadAddressPath.contains("MP4")){
            response.setContentType("video/mp4");
        }else{
            response.setContentType("application/octet-stream;charset=utf-8");
        }
        Path uploadPath = Paths.get(uploadAddressPath);
        //设置响应头
        response.setHeader("Content-Disposition", "attachment;fileName=" +
                URLEncoder.encode(uploadPath.toFile().getName(), StandardCharsets.UTF_8));// 设置文件名
        try (
                BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(uploadPath));
                // 输出流
                BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())
        ) {
            byte[] buff = new byte[1024];
            int len;
            while ((len = bis.read(buff)) > 0) {
                bos.write(buff, 0, len);
            }
        } catch (IOException e) {
            log.error("预览资源文件失败：{}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void webDown(int[] ids, HttpServletResponse response) {
        try {
            //设置响应头信息
            response.setContentType("application/zip");

            //批量下载情况
            if (ids.length > 1) {

                //设置响应头
                String MultiPleZipFileName = "MultipleResource_" + System.currentTimeMillis() + FileSuffixConstants._ZIP;
                response.setHeader("Content-Disposition", "attachment;fileName=" +
                        URLEncoder.encode(MultiPleZipFileName, StandardCharsets.UTF_8));

                //创建zip输出流
                ServletOutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os);

                for (int id : ids) {
                    ResourceInfo resourceInfo = resourceInfoService.getById(id);
                    //待下载的文件目录路径
                    String dirName = resourceInfo.getArtResName();
                    //创建压缩包条目-就是在压缩包里面新建目录,将此目录作为待下载的文件存储地方
                    String zipEntryPath = dirName + "/";
                    zos.putNextEntry(new ZipEntry(zipEntryPath));

                    File downLoadDirectory = Paths.get(resourceInfo.getArtResVersion(), fileSavePath_,
                            projectNamePrefix + SecurityUtil.getAppId(), dirName).toFile();

                    //将文件夹内的文件逐个写入ZIP
                    File[] files = downLoadDirectory.listFiles();
                    assert files != null;
                    for (File file : files) {
                        //将files存储在zipEntryPath下，如果传"",就直接下载到压缩包最外层目录下。
                        addFileToZip(zipEntryPath, file, zos);
                    }
                    //完成当前压缩包的写入
                    zos.closeEntry();
                }

                //关闭zip输出流流
                zos.close();

                //单个资源下载情况
            } else if (ids.length == 1) {
                //创建zip输出流
                ServletOutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os);

                ResourceInfo resourceInfo = resourceInfoService.getById(ids[0]);
                //待下载的文件目录路径
                String dirName = resourceInfo.getArtResName();
                String directoryPath =  resourceInfo.getArtResVersion() + "/" + fileSavePath_ + projectNamePrefix + SecurityUtil.getAppId() + "/" + dirName;
                File downLoadDirectory = new File(directoryPath);
                //构建压缩包包名,由于目录名太长，取资源名
                String zipFileName = dirName + FileSuffixConstants._ZIP;

                //设置响应头
                response.setHeader("Content-Disposition", "attachment;fileName=" +
                        URLEncoder.encode(zipFileName, StandardCharsets.UTF_8));

                //将文件夹内的文件逐个写入ZIP
                File[] files = downLoadDirectory.listFiles();
                assert files != null;
                for (File file : files) {
                    addFileToZip("", file, zos);
                }
                //完成当前压缩包的写入
                zos.closeEntry();
                //关闭流
                zos.close();
            }

        } catch (Exception e) {
            log.error("资源导出和下载功能异常{}",e.getMessage());
        }
    }

    @Override
    public void webDownMinio(int[] ids, HttpServletResponse response) {
        try (ServletOutputStream os = response.getOutputStream();
             ZipOutputStream zos = new ZipOutputStream(os)) {
            // 设置响应头信息
            response.setContentType("application/zip");

            String zipFileName = getZipFileName(ids);
            response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(zipFileName, StandardCharsets.UTF_8));

            for (int id : ids) {
                ResourceInfo resourceInfo = getResourceInfo(id);
                String downLoadPath = getDownloadPath(resourceInfo);
                String parentFolder = "";
                if (ids.length > 1) {
                    // 多个文件下载时，保留上级文件夹
                    parentFolder = resourceInfo.getArtResName() + File.separator;
                }
                processMinioObjects(parentFolder, downLoadPath, zos);
            }
            zos.finish();
        } catch (Exception e) {
            log.error("资源导出和下载功能异常: {}", e.getMessage());
        }
    }

    private String getZipFileName(int[] ids) {
        if (ids.length > 1) {
            // 批量下载情况
            return "MultipleResource_" + System.currentTimeMillis() + FileSuffixConstants._ZIP;
        } else {
            // 单个资源下载情况
            ResourceInfo resourceInfo = getResourceInfo(ids[0]);
            String dirName = resourceInfo.getArtResName();
            return dirName + FileSuffixConstants._ZIP;
        }
    }

    private ResourceInfo getResourceInfo(int id) {
        return resourceInfoService.getById(id);
    }

    private String getDownloadPath(ResourceInfo resourceInfo) {
        return Paths.get(resourceInfo.getArtResVersion(), fileSavePath_,
                projectNamePrefix + SecurityUtil.getAppId(), resourceInfo.getArtResName()).toString();
    }

    private void processMinioObjects(String parentFolder, String baseObjectName, ZipOutputStream zos) {
        Iterable<Result<Item>> results = minioUtil.listObjects(bucketName, baseObjectName, true);
        Iterator<Result<Item>> iterator = results.iterator();
        while (iterator.hasNext()) {
            try {
                Item item = iterator.next().get();
                String objectName = item.objectName();
                String relativePath = objectName.substring(baseObjectName.length());
                String zipEntryPath = parentFolder + relativePath;

                if (item.isDir()) {
                    // 如果是文件夹，创建 ZIP 条目
                    zos.putNextEntry(new ZipEntry(zipEntryPath + "/"));
                    zos.closeEntry();
                } else {
                    // 如果是文件，下载并写入 ZIP
                    try (java.io.InputStream stream = minioUtil.download(bucketName, objectName)) {
                        zos.putNextEntry(new ZipEntry(zipEntryPath));
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = stream.read(buffer)) > 0) {
                            zos.write(buffer, 0, length);
                        }
                        zos.closeEntry();
                    }
                }
            } catch (Exception e) {
                log.error("处理 MinIO 对象时出错: {}", e.getMessage());
            }
        }
    }


    static void addFileToZip(String Path, File file, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            String subdirectoryPath = Path + file.getName() + "/";
            zos.putNextEntry(new ZipEntry(subdirectoryPath));
            File[] files = file.listFiles();
            assert files != null;
            for (File f : files) {
                addFileToZip(subdirectoryPath, f, zos);
            }
        } else {
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            zos.putNextEntry(new ZipEntry(Path + file.getName()));
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            fis.close();
        }
    }

    @Override
    public String deleteFileWeb(ResourceInfo resourceInfo) {
        //1.只刪除指定文件的资源文件表数据，根据uniqueId删除
        LambdaQueryWrapper<ResourceInfo> queryWrapper = new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getUniqueId, resourceInfo.getUniqueId());

        List<ResourceInfo> resourceInfos = resourceInfoService.list(queryWrapper);
        if (resourceInfos.isEmpty()){
            throw JtyfBusinessException.of(ErrorCodeConstants.RESOURCE_NOT_EXISTS);
        }
        //获取资源文件夹名字
        String artResName = resourceInfos.get(0).getArtResName();
        //根据uniqueId删除资源表指定文件数据;
        resourceInfoService.getBaseMapper().delete(queryWrapper);
        //2.判断资源表中该文件夹所在的目录是否删除完：若删除完就删除fileInfo和fileOnlyInfo表以及服务器相关资源
        List<ResourceInfo> resourceInfoList = resourceInfoService.list(new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getArtResName, artResName));
        if(resourceInfoList.isEmpty()){
            //资源表中artResName资源删除完了
            //删除文件夹本身及文件夹下的文件数据 + 资源服务器数据
            List<FileOnlyInfo> fileOnlyInfos = fileOnlyInfoMapper.selectList(new LambdaQueryWrapper<FileOnlyInfo>().eq(FileOnlyInfo::getParentName, artResName));

            if (!fileOnlyInfos.isEmpty()){
                String uniqueId = fileOnlyInfos.get(0).getUniqueId();
                fileInfoMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUniqueId, uniqueId));
                fileOnlyInfoMapper.delete(new LambdaQueryWrapper<FileOnlyInfo>().eq(FileOnlyInfo::getUniqueId, uniqueId));
            }
            String fileSavePath = Paths.get(resourceInfo.getArtResVersion(), fileSavePath_ + projectNamePrefix + SecurityUtil.getAppId(), artResName).toString();

            FileUtils.deleteFolders(fileSavePath);

            threadPoolTaskExecutor.execute(() -> {
                try {
                    deleteMinio(bucketName, fileSavePath);
                } catch (Exception e) {
                    log.error("删除文件：文件获取Minio失败：{}", e.getMessage());
                }
            });

            return "该资源所在的资源目录删除完了,已删除资源目录相关数据及服务器资源";
        }
        return "该资源所在的资源目录未删除完,只删除资源表";
    }

    private void deleteMinio(String bucketName, String fileSavePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        //
        List<Item> objs =  minioUtil.getAllObjectsByPrefix(bucketName,fileSavePath,true);
        objs.forEach(item -> {
            try {
                minioUtil.deleteObject(bucketName,item.objectName());
            } catch (Exception e) {
                log.error("删除文件：文件删除Minio失败：{}", e.getMessage());
            }
        });
    }

    @Override
    public Boolean updateFileName(FileInfoUpdateNameVO fileInfoUpdateNameVo) {
        String filePath = getDownloadPath(fileInfoUpdateNameVo.getParentId(), 1);
        File pdfFileOld = Paths.get(fileSavePath_, filePath, fileInfoUpdateNameVo.getFileName()).toFile();
        File pdfFileNew = Paths.get(fileSavePath_, filePath, fileInfoUpdateNameVo.getNewFileName()).toFile();

        boolean isUpdateTrue = pdfFileOld.renameTo(pdfFileNew);
        if (isUpdateTrue) {
            FileInfo szl3dFileInfo = getById(fileInfoUpdateNameVo.getId());
            szl3dFileInfo.setFileName(fileInfoUpdateNameVo.getNewFileName());
            updateById(szl3dFileInfo);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<?> uploadWeb(MultipartFile[] uploadFiles) {
        String filePath = "";
        for (MultipartFile uploadFile : uploadFiles) {

            String fileName = FileUtils.getFileName(uploadFile.getOriginalFilename());
            String suffix = FileUtils.getFileSuffixLowerCase(uploadFile.getOriginalFilename());
            //判断文件名
            if (StringUtils.isEmpty(fileName)) {
                return CommonResult.error(500,"文件名错误");
            }
            //判断资源类型
            if (!FileSuffixConstants.ZIP.equals(suffix)) {
                return CommonResult.error(500,"文件类型错误,请上传zip类型的文件");
            }

            // 压缩包的uploadPath=项目名/，解压父目录不存在时，则需要创建目录
            String uploadPath = Paths.get(projectNamePrefix).toString();
            log.info("项目名为：" + uploadPath);
            File folder = new File(fileSaveTemporaryPath + File.separator + uploadPath);
            if (!folder.exists() && !folder.mkdirs()) {
                throw JtyfBusinessException.of(ErrorCodeConstants.FILE_CREATE_DIRECTORY_FAIL);
            }
            log.info("文件存储目录：{}", folder.getAbsolutePath());

            //解压父目录存在时
            try {
                Path targetPath = Paths.get(folder.getPath(), uploadFile.getOriginalFilename());
                Files.copy(uploadFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                filePath = Paths.get(uploadPath, uploadFile.getOriginalFilename()).toString();
            } catch (IOException e) {
                log.error("上传文件失败：{}", e.getMessage());
            }
        }
        //获取包路径：PSFramework/压缩包包名
        return CommonResult.success(filePath);
    }

    private boolean isExist(File directory, File jsonFile, String zipAbsoluteFilePath) throws IOException {
//              //读-解-获-存-判(存或修)
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonFile);
        //log.info("jsonNode:" +jsonNode);-调试用
        if (jsonNode.size() != 1 || !jsonNode.isObject()) {
            throw new IllegalArgumentException("DescriptionFile.json配置文件中只能含有一个key:value,且为对象类型");
        }
        String key = jsonNode.fieldNames().next();
        //log.info("json的key为" + key);-调试用
       JsonNode valueNode = jsonNode.get(key);
        //log.info("json的value为" + valueNode.toString());-调试用
        String uniqueId = valueNode.get("uniqueId").asText();
        List<ResourceInfo> list = resourceInfoService.getBaseMapper().selectList(new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getUniqueId, uniqueId));
        if (!list.isEmpty()) {
            FileUtils.deleteFolders(directory.getAbsolutePath());
            log.info("该模型已存在,临时压缩包删除成功");

            if (new File(zipAbsoluteFilePath).delete()) {
                log.info("该模型已存在,临时解压目录以及压缩包删除成功");
            }
            return true;
        } else {
            return false;
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<?> uploadSaveUpdate(String filePath, ResourceInfo resourceInfo) throws IOException {
        //判断是否是更新操作
        if (filePath == null || filePath.isEmpty()) {
            resourceInfo.setUpdateUser(SecurityUtil.getUsername());
            resourceInfoService.updateById(resourceInfo);
            return CommonResult.success("更新成功");
        }
        // 将资源版本号 写入resourceInfo
        fillResourceVersion(resourceInfo);

        //第一层判断:是否是压缩包
        String fileSavePath = resourceInfo.getArtResVersion() + File.separator + fileSavePath_;
        String absoluteFilePath = fileSavePath + filePath;
        String fileExtension = getFileExtension(absoluteFilePath);
        log.info("文件扩展名为:" + fileExtension);
        if (!fileExtension.equalsIgnoreCase(FileSuffixConstants.ZIP)) {
            throw JtyfBusinessException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_ZIP);
        }

        // 文件的移动，必须先判断是否压缩包，再移动
        File sourceFile = new File(fileSaveTemporaryPath + File.separator + filePath);
        moveZipFile(sourceFile, fileSavePath, filePath);

        //第二层判断：校验:压缩包只能含有一个obj/fbx/3ds文件
        String zipFilePath = fileSavePath + filePath;
        if (!validateZipContents(zipFilePath)) {
            throw JtyfBusinessException.of(ErrorCodeConstants.ZIP_FILE_ONLY_MODEL_FAIL);
        }
        log.info("压缩包中只含有一个obj/fbx/3ds文件");

        //解压文件, unZipDirPath:/home/app/flask-file-server-master/tmp/PSFramework/
        String unZipRootDirPath = fileSavePath + projectNamePrefix + SecurityUtil.getAppId() + File.separator;
        String unzipDirectoryPath = ZipUtils.unzipBuildNameAndDelete(zipFilePath, unZipRootDirPath);
        return handleUnzipDataFile(resourceInfo, sourceFile, new File(unzipDirectoryPath));
    }

    private void fillResourceVersion(ResourceInfo resourceInfo) {
        // 获取文件资源版本信息
        if (resourceInfo.getArtResVersionId() == null || resourceInfo.getArtResVersionId() == 0) {
            throw JtyfBusinessException.of(ErrorCodeConstants.VERSION_ID_IS_NULL);
        }
        ResourceVersion resourceVersion = resourceVersionService.getBaseMapper().selectById(resourceInfo.getArtResVersionId());
        if (resourceVersion == null) {
            throw JtyfBusinessException.of(ErrorCodeConstants.VERSION_ID_NOT_EXISTS);
        }
        resourceInfo.setArtResVersion(resourceVersion.getVersionName());
    }

    private void moveZipFile(File sourceFile, String fileSavePath, String filePath) {
        File destFile = new File(fileSavePath + filePath);
        File dest = Paths.get(fileSavePath, projectNamePrefix).toFile();
        // 如果文件目录不存在，则需要创建目录
        if (!dest.exists() && !dest.mkdirs()) {
            throw JtyfBusinessException.of(ErrorCodeConstants.FILE_CREATE_DIRECTORY_FAIL);
        }
        log.info("文件存储目录：{}", dest.getAbsolutePath());

        try {
            Files.move(sourceFile.toPath(), destFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件移动成功,目标文件路径为：{}", destFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("文件移动失败,目标文件路径为：{},报错信息{}", destFile.getAbsolutePath(), e.getMessage());
            throw JtyfBusinessException.of(ErrorCodeConstants.FILE_MOVE_FAIL);
        }
    }

    private CommonResult<String> handleUnzipDataFile(ResourceInfo resourceInfo, File sourceFile, File unzipFile) throws IOException {
        File[] files = Optional.ofNullable(unzipFile.listFiles())
                .orElseThrow(() -> JtyfBusinessException.of(ErrorCodeConstants.UNZIP_FILE_IS_NULL));
        //含有子目录情况,将子目录的文件移动到解压目录下并删除子目录
        if (files[0].isDirectory() && files.length == 1) {
            moveDirectoryAndDelete(files[0], unzipFile);
        } else if (files.length > 1 && hasOnlyDirectory(files)) {
            FileUtils.deleteFolders(unzipFile.getAbsolutePath());
            return CommonResult.error(500,"压缩包存在多个资源,只能上传一个资源，该压缩包资源已删除,请重新上传");
        }

        threadPoolTaskExecutor.execute(() -> batchUploadMinio(files));

        // unzipFile里面的子目录可能已经被移动过，所以需要重新获取
        File[] newFiles = Optional.ofNullable(unzipFile.listFiles())
                .orElseThrow(() -> JtyfBusinessException.of(ErrorCodeConstants.UNZIP_FILE_IS_NULL));
        // 移动后的解压目录下的文件
        for (File file : newFiles) {
            //只剩散装文件情况
            //判断是obj/FBX文件还是gltf文件,若是obj/FBX文件,找到obj/FBX文件进行转换
            //第三层判断：只含有一个obj/fbx文件
            if (!file.isFile()) {
                continue;
            }

            String fileSuffix = FileUtils.getFileSuffixLowerCase(file.getName());
            if (ModelConvertConstants.MODEL_SRC_SET.contains(fileSuffix)) {
                return getObjFbxGltfResult(resourceInfo, sourceFile, file);
            } else if (ModelConvertConstants.MODEL_TARGET_SET.contains(fileSuffix)) {
                return getGltfResult(resourceInfo, sourceFile, file);
            }
        }
        throw JtyfBusinessException.of(ErrorCodeConstants.OVER);
    }

    private void batchUploadMinio(File[] files) {
        try {
            for (File file : files) {
                uploadMinio(file);
            }
        } catch (Exception e) {
            log.error("文件上传Minio失败：{}", e.getMessage());
        }
    }

    private void uploadMinio(File file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (file.isDirectory()){
            File[] files = file.listFiles();
            for (File fileRecursion : files) {
                uploadMinio(fileRecursion);
            }
        }else if(file.isFile()){
            minioUtil.uploadFile(bucketName, file.getPath(), file.getPath());
        } else {
            File[] files = file.getParentFile().listFiles();
            for (File fileRecursion : files) {
                uploadMinio(fileRecursion);
            }
        }

    }

    static boolean hasOnlyDirectory(File[] files) {
        assert files != null;
        for (File file : files) {
            if(file.isFile()){
                return false;
            }
        }
        return true;
    }

   public static void moveDirectoryAndDelete(File sourceDirectory, File destDirectory) {
        //log.info("将"+sourceDirectory.getAbsolutePath()+"移动到"+destDirectory.getAbsolutePath()+"的方法");
        if (sourceDirectory.isDirectory() && destDirectory.isDirectory()) {
            //获取源目录下的所有文件
            File[] files = sourceDirectory.listFiles();
            if (files != null) {
                String newFilePath = destDirectory.getAbsolutePath() + "/";
                for (File file : files) {
                    try {
                        if (file.isDirectory()) {
                            newFilePath += file.getName() + "/";
                            File newFile = new File(newFilePath);
                            if(!newFile.exists()){
                                if(newFile.mkdirs()){
                                    log.info("资源子文件夹内存在子目录，新建成功");
                                }
                            }
                            //如果是子目录，则递归调用自身移动目录的方法
                            moveDirectoryAndDelete(new File(file.getAbsolutePath()), newFile);
                        } else {
                            String newFilePath1 = destDirectory.getAbsolutePath()+ "/" + file.getName();
                            File newFile = new File(newFilePath1);
                            //如果是文件，则移动到目标目录
                            Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        }
                    } catch (IOException e) {
                        log.error("源目录删除失败：{}", e.getMessage());
                    }
                }
                //移动完成，删除源目录
                FileUtils.deleteFolders(sourceDirectory.getAbsolutePath());
                log.info("源目录删除成功");
            }

        }
    }

    private CommonResult<?> getConfigResult(ResourceInfo resourceInfo, File sourceFile, String unZipDirName, File directory, File jsonFile) throws IOException {
        //读-解-获-存-判(存或修)
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(jsonFile);
        //log.info("jsonNode:" +jsonNode);
        if (jsonNode.size() != 1 || !jsonNode.isObject()) {
            throw new IllegalArgumentException("DescriptionFile.json配置文件中只能含有一个key:value,且为对象类型");
        }
        //JsonNode entryNode = jsonNode.elements().next();
        //System.out.println("entryNode:" +entryNode);
        String key = jsonNode.fieldNames().next();
        //log.info("json的key为" + key);
        JsonNode valueNode = jsonNode.get(key);
        //log.info("json的value为" + valueNode.toString());
        String artResName = valueNode.get("artResName").asText();
        String assetBundelName = valueNode.get("assetBundelName").asText();
        String assetName = valueNode.get("assetName").asText();
        String uniqueId = valueNode.get("uniqueId").asText();
        if(resourceInfo.getId() !=null && resourceInfo.getId() != 0){
            //更新操作，不更改uniqueId,改回原来uniqueId
            uniqueId = resourceInfoService.getBaseMapper().selectById(resourceInfo.getId()).getUniqueId();
        }
        String previewPath = "";
        //暂不处理这个资源地址，预览后面做
        //String previewPath = serverPath+"/file-info/download-resource?uploadPath=" + unZipDirPath + assetBundelName;
        //若解压目录名称不等于artResName时,将解压文件目录改成artResName属性值
        if (!unZipDirName.equals(artResName)) {
            String newDirectoryPath = directory.getAbsolutePath().replace(directory.getName(), artResName);
            File newDirectory = new File(newDirectoryPath);
            boolean success = directory.renameTo(newDirectory);
            if (success) {
                log.info("修改后的解压文件目录：{}", newDirectoryPath);
            } else {
                return CommonResult.error(500,"有配置文件-压缩包解压目录名改名为artResName失败！！");
            }
        }
        //存入文件表和文件唯一表--这里必须在改名之后传，这样artResName就能在文件唯一表的parentName找打对应资源
        save(SecurityUtil.getAppId());
        //判断修改还是添加
        insertOrUpdate(resourceInfo, sourceFile, artResName, assetName, assetBundelName, uniqueId, previewPath);
        return CommonResult.success("压缩包中存在配置文件，上传模型成功");
    }

    //判断是obj/FBX文件还是gltf文件,若是obj/FBX文件,找到obj/FBX文件进行转换
    private CommonResult<String> getObjFbxGltfResult(ResourceInfo resourceInfo, File sourceFile, File file) throws IOException {
        //第三层判断：只含有一个obj/fbx/3ds文件
        //输入文件路径
        String inputFilePath = file.getAbsolutePath();
        //log.info("obj/fbx/3ds文件绝对路径：" + inputFilePath);
        //构造新的GLTF格式的文件,这里多了一层子目录
        String gltfFilePath = inputFilePath.substring(0, inputFilePath.lastIndexOf(".")) + FileSuffixConstants._GLTF;
        //转化格式并存表
        //转换为GLTF格式文件
        convertToGltfUtil.convertToGltf(inputFilePath, gltfFilePath);
        log.info("已转为gltf格式");
        //删除原始obj/fbx文件
//        if (file.delete()) {
//            log.info("原始obj/fbx/max/3ds文件删除成功");
//        } else {
//            return CommonResult.error(500,"原始obj/fbx/max/3ds文件删除失败");
//        }
        //路径转换--"\\"转"/"
        GLTFPathConverterUtil.gltfPathConverter(gltfFilePath);
        //fileInfoService.saveUnzipFile(new File(unzipDirectoryPath+"/"),pSFrameworkId,unZipDirName);

        save(SecurityUtil.getAppId());
        //artResName:资源文件目录;
        //assetName：资源文件名称
        //assetBundelName：资源文件相对路径；
        File gltfFile = new File(gltfFilePath);
        String artResName = gltfFile.getParentFile().getName();
        String assetName = file.getName().substring(0, file.getName().lastIndexOf(".")) + FileSuffixConstants._GLTF;
        String assetBundelName = artResName + "/" + assetName;
        String uniqueId = UUID.randomUUID().toString();
        if(resourceInfo.getId() != null && resourceInfo.getId() != 0){
            //更新操作，不更改uniqueId,改回原来uniqueId
            uniqueId = resourceInfoService.getBaseMapper().selectById(resourceInfo.getId()).getUniqueId();
        }
//        String previewPath = serverPath + "/file-info/download-resource?uploadPath=" + gltfFilePath.replaceAll("\\\\", "/");

        String artResRelativePath = file.getParent().replaceAll("\\\\", "/") + "/" + assetName;

        //判断修改还是添加
        insertOrUpdate(resourceInfo, sourceFile, artResName, assetName, assetBundelName, uniqueId, artResRelativePath);

        threadPoolTaskExecutor.execute(() -> {
            try {
                minioUtil.uploadFile(bucketName, artResRelativePath, gltfFilePath);
                String binName = file.getName().substring(0, file.getName().lastIndexOf(".")) + ".bin";

                String binPath = gltfFilePath.substring(0, gltfFilePath.lastIndexOf(".")) + ".bin";

                minioUtil.uploadFile(bucketName, file.getParent() + "/" + binName , binPath);

            } catch (Exception e) {
                log.error("gltf文件上传Minio失败：{}", e.getMessage());
            }
        });
        return CommonResult.success("压缩包中且存在一个obj/fbx文件，上传模型成功");
    }

    private CommonResult<String> getGltfResult(ResourceInfo resourceInfo, File sourceFile, File file) throws IOException {
        save(SecurityUtil.getAppId());
        //只含有一个gltf文件
        //输入gltf文件绝对路径
        String gltfFilePath = file.getAbsolutePath();
        //路径转换
        GLTFPathConverterUtil.gltfPathConverter(gltfFilePath);
        //artResName:  资源文件目录;
        //assetName：  资源文件名称;
        //assetBundelName：资源文件相对路径;
        File gltfFile = new File(gltfFilePath);
        String artResName = gltfFile.getParentFile().getName();
        String assetName = gltfFile.getName();
        String assetBundelName = artResName + "/" + assetName;
        String uniqueId = UUID.randomUUID().toString();
        if(resourceInfo.getId() != null && resourceInfo.getId() != 0){
            //更新操作，不更改uniqueId,改回原来uniqueId
            uniqueId = resourceInfoService.getBaseMapper().selectById(resourceInfo.getId()).getUniqueId();
        }
//        String previewPath = serverPath + "/file-info/download-resource?uploadPath=" + gltfFilePath.replaceAll("\\\\", "/");

        String artResRelativePath = file.getParent().replaceAll("\\\\", "/") + "/" + assetName;

        //判断修改还是添加
        insertOrUpdate(resourceInfo, sourceFile, artResName, assetName, assetBundelName, uniqueId, artResRelativePath);
        return CommonResult.success("压缩包中存在一个gltf文件，上传模型成功");
    }

    @Override
    public void insertOrUpdate(@RequestBody ResourceInfo resourceInfo, File sourceFile, String artResName, String assetName, String assetBundelName, String uniqueId, String artResRelativePath) {
        if (resourceInfo.getId() == null || resourceInfo.getId() == 0) {
            //添加
            resourceInfo.setArtResName(artResName);
            resourceInfo.setAssetBundelName(assetBundelName);
            resourceInfo.setAssetName(assetName);
            resourceInfo.setUpdateUser(SecurityUtil.getUsername());
            resourceInfo.setUniqueId(uniqueId);
            resourceInfo.setArtResRelativePath(artResRelativePath);
            resourceInfo.setUpdateTime(LocalDateTime.now());
            resourceInfoService.save(resourceInfo);
            log.info("上传成功");
        } else {
            //修改
            ResourceInfo resourceById = resourceInfoService.getById(resourceInfo.getId());
            if (resourceById != null) {
                String oldArtResName = resourceById.getArtResName();
                FileUtils.deleteFolders(resourceInfo.getArtResVersion() + "/" + fileSavePath_ + projectNamePrefix + SecurityUtil.getAppId() + "/" + oldArtResName);
                List<FileInfo> szl3dFileInfos = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getFileName,oldArtResName));

                if (!szl3dFileInfos.isEmpty()) {
                    String fileUniqueId = szl3dFileInfos.get(0).getUniqueId();
                    fileInfoMapper.delete(new LambdaQueryWrapper<FileInfo>().eq(FileInfo::getUniqueId, fileUniqueId));
                    fileOnlyInfoMapper.delete(new LambdaQueryWrapper<FileOnlyInfo>().eq(FileOnlyInfo::getUniqueId,fileUniqueId));
                }
            }
            resourceInfo.setArtResName(artResName);
            resourceInfo.setAssetBundelName(assetBundelName);
            resourceInfo.setAssetName(assetName);
            resourceInfo.setUpdateUser(SecurityUtil.getUsername());
            resourceInfo.setUniqueId(uniqueId);
            resourceInfo.setArtResRelativePath(artResRelativePath);
            resourceInfo.setUpdateTime(LocalDateTime.now());
            resourceInfoService.updateById(resourceInfo);
            log.info("修改成功");
        }
    }

    @Override
    public Integer saveFileInfo(String projectId) throws IOException {
        this.baseMapper.delete(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getAppId,projectId));
        fileOnlyInfoMapper.delete(new LambdaQueryWrapper<FileOnlyInfo>()
                .eq(FileOnlyInfo::getAppId,projectId));
        File file = new File(fileSavePath_);
        log.info("更新资源列表：filePath:{},getAbsolutePath: {}", fileSavePath_, file.getAbsolutePath());
        return saveFilePath(file, 0, 0, "", "0",projectId);
    }
}