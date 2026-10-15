package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.entity.BatchConversion;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.BatchConversionMapper;
import cn.iocoder.wx.module.dimensions.service.BatchConversionService;
import cn.iocoder.wx.module.dimensions.service.FileInfoService;
import cn.iocoder.wx.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.wx.module.dimensions.service.constant.ModelConvertConstants;
import cn.iocoder.wx.module.dimensions.utils.*;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class BatchConversionServiceImpl extends ServiceImpl<BatchConversionMapper, BatchConversion> implements BatchConversionService {


    @Value("${batch-conversion-actuality-path}")
    private String batchConversionActualityPath;

    @Resource
    FileInfoService fileInfoService;

    @Resource
    BatchConversionMapper batchConversionMapper;


    @Override
    public BatchConversion uploadToWeb(MultipartFile uploadFile) {
        String uploadFileOldName = uploadFile.getOriginalFilename();
        //判断文件名
        String name = FileUtils.getFileName(uploadFileOldName);
        if (StringUtils.isEmpty(name)) {
            throw WxBusinessException.of(ErrorCodeConstants.FILE_NAME_ERROR);
        }
        //判断资源类型
        String suffix = FileUtils.getFileSuffixLowerCase(uploadFileOldName);
        if (!FileSuffixConstants.ZIP.equals(suffix)) {
            throw WxBusinessException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_ZIP);
        }
        //改名为唯一
        String uploadFileNewName = name + UUID.randomUUID() + FileSuffixConstants._ZIP;
        String uploadFileNewPath = batchConversionActualityPath + uploadFileNewName;
        File folder = new File(batchConversionActualityPath);
        Path targetDirectory = Paths.get(folder.getPath().replaceAll("\\\\", "/"),uploadFileNewName);
        //解压父目录不存在时
        if (!folder.isDirectory()) {
            if (folder.mkdirs()) {
                log.info("文件临时存储目录创建成功");
            } else {
                throw WxBusinessException.of(ErrorCodeConstants.FILE_CREATE_DIRECTORY_FAIL);
            }
        }
        try {
            Files.copy(uploadFile.getInputStream(), targetDirectory, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("复制文件失败：{}", e.getMessage());
        }
        if(fileInfoService.validateZipContents(uploadFileNewPath)){
            //说明只含有一个obj/fbx/gltf文件，通过校验
            //存入表
            try (ZipFile temporaryzipFile = new ZipFile(uploadFileNewPath, Charset.forName("GBK"))) {
                Enumeration<? extends ZipEntry> temporaryEntries = temporaryzipFile.entries();
                while (temporaryEntries.hasMoreElements()) {
                    ZipEntry entry = temporaryEntries.nextElement();
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (ModelConvertConstants.MODEL_SRC_SET.contains(FileUtils.getFileSuffixLowerCase(entryName))) {
                            BatchConversion batchConversion = new BatchConversion();
                            int index = entryName.lastIndexOf('.');
                            batchConversion.setResourceWebName(entryName.substring(0,index));
                            batchConversion.setResourceType(entryName.substring(index));
                            double resourceSize = (double) entry.getSize() / 1048576;
                            batchConversion.setResourceSize(resourceSize);
                            batchConversion.setUploadFileName(uploadFileOldName);
                            batchConversion.setUniqueUploadFileName(uploadFileNewName);
                            batchConversion.setConversionStatus("Not Converted");
                            batchConversion.setUserName(SecurityUtil.getUsername());
                            batchConversionMapper.insert(batchConversion);
                            //返回压缩包名字
                            return batchConversion;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("存转换表失败：{}", e.getMessage());
                throw WxBusinessException.of(ErrorCodeConstants.STORAGE_CONVERSION_TABLE_FAIL);
            }

        }
        if(new File(uploadFileNewPath).delete()){
            throw WxBusinessException.of(ErrorCodeConstants.CHECK_FAIL_DELETE_ARCHIVE);
        }
        throw WxBusinessException.of(ErrorCodeConstants.CHECK_FAIL_DELETE_ARCHIVE_FAIL);
   }

    @Override
    public CommonResult<?> toGltf(Integer id) throws IOException {
        BatchConversion batchConversion = batchConversionMapper.selectById(id);
        if(batchConversion == null){
            throw WxBusinessException.of(ErrorCodeConstants.BATCH_CONVERSION_IS_EMPTY);
        }
        String uploadNewFileName = batchConversion.getUniqueUploadFileName();
        String uploadNewFilePath = batchConversionActualityPath + uploadNewFileName;

        //解压文件
        String unzipDirectoryPath = ZipUtils.unzipAndDelete(uploadNewFilePath, batchConversionActualityPath);
        File unzipDirectory = new File(unzipDirectoryPath);
        File[] files = unzipDirectory.listFiles();
        //System.out.println("解压目录下的文件及目录："+Arrays.toString(files));-调试用

        //含有子目录情况,将子目录的文件移动到解压目录下并删除子目录
        if(files==null || files.length==0){
            batchConversion.setConversionStatus("Conversion Failed");
            batchConversionMapper.updateById(batchConversion);
            return CommonResult.error(500,"文件为空，转换结果：失败");
        }
        if (files[0].isDirectory() && files.length == 1) {
            FileInfoServiceImpl.moveDirectoryAndDelete(files[0], unzipDirectory);
        } else if (files.length > 1 && FileInfoServiceImpl.hasOnlyDirectory(files)) {
            FileUtils.deleteFolders(unzipDirectory.getAbsolutePath());
            batchConversion.setConversionStatus("Conversion Failed");
            batchConversionMapper.updateById(batchConversion);
            return CommonResult.error(500,"压缩包存在多个资源,不能转换,该压缩包资源已删除");
        }
        File[] filesNew = unzipDirectory.listFiles();
        //System.out.println("移动后的解压目录下的文件"+Arrays.toString(files1));-调试用
        assert filesNew != null;
        for (File file : filesNew) {
            //只剩散装文件情况
            //只含有一个obj/fbx/3ds文件
            String fileSuffix = FileUtils.getFileSuffixLowerCase(file.getName());
            if (file.isFile() && ModelConvertConstants.MODEL_SRC_SET.contains(fileSuffix)) {
                //obj/fbx/3ds文件文件路径
                String inputFilePath = file.getAbsolutePath();
                //log.info("obj/fbx/3ds文件绝对路径：" + inputFilePath);
                //构造新的GLTF格式的文件,这里多了一层子目录
                String gltfFilePath = inputFilePath.substring(0, inputFilePath.lastIndexOf(".")) + FileSuffixConstants._GLTF;
                //转化格式并存表
                //转换为GLTF格式文件
                convertToGltfUtil.convertToGltf(inputFilePath, gltfFilePath);
                if(new File(gltfFilePath).exists()){
                    batchConversion.setConversionStatus("Conversion Successful");
                } else {
                    batchConversion.setConversionStatus("Conversion Failed");
                }
                log.info("已转为gltf格式");
                //删除原始obj/fbx文件
                if (file.delete()) {
                    log.info("原始obj/fbx/3ds文件删除成功");
                } else {
                    batchConversion.setConversionStatus("Conversion Failed");
                    batchConversionMapper.updateById(batchConversion);
                    return CommonResult.error(500,"原始obj/fbx/3ds文件删除失败,转换失败");
                }
                //路径转换--"\\"转"/"
                GLTFPathConverterUtil.gltfPathConverter(gltfFilePath);
                batchConversion.setResourceDirectoryName(unzipDirectory.getName());
                batchConversionMapper.updateById(batchConversion);
                return CommonResult.success("转换结果：成功");
            }
        }
        batchConversion.setConversionStatus("Conversion Failed");
        batchConversionMapper.updateById(batchConversion);
        return CommonResult.error(500,"转换结果：失败");
    }

    @Override
    public void deleteAll() {
        LambdaQueryWrapper<BatchConversion> queryByUserName = new LambdaQueryWrapper<>();
        //删除指定用户表的相关数据
        //TODO 获取登录名称
        if(SecurityUtil.getUsername() != null){
            queryByUserName = new LambdaQueryWrapper<BatchConversion>()
                    .eq(BatchConversion::getUserName, SecurityUtil.getUsername());
        }
        //删除指定用户的资源文件夹
        List<BatchConversion> batchConversions = batchConversionMapper.selectList(queryByUserName);
        for (BatchConversion batchConversion : batchConversions) {
            String resourceDirectoryName = batchConversion.getResourceDirectoryName();
            if(resourceDirectoryName !=null){
                FileUtils.deleteFolders(batchConversionActualityPath + resourceDirectoryName);
            }
        }
        //删除表
        batchConversionMapper.delete(queryByUserName);
        //删除上传了但没有转发的压缩包--清理脏压缩包
        File[] files = new File(batchConversionActualityPath).listFiles();
       if (files != null){
           for (File file : files) {
                if(file.isFile() && FileInfoServiceImpl.getFileExtension(file.getAbsolutePath()).equalsIgnoreCase(FileSuffixConstants.ZIP)){
                    if(file.delete()){
                        log.info("压缩包删除成功");
                    }
                }
           }
       }
    }

    @Override
    public void downloadGltf(int[] ids, HttpServletResponse response) {
        if (ids.length == 0) {
            log.warn("批量下载转换文件失败:ids为空");
            return;
        }

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
                    BatchConversion batchConversion = batchConversionMapper.selectById(id);

                    //待下载的文件目录路径
                    String resourceDirectoryName = batchConversion.getResourceDirectoryName();
                    //创建压缩包条目-就是在压缩包里面新建目录,将此目录作为待下载的文件存储地方
                    String resourceDirectoryPath = resourceDirectoryName + "/";
                    zos.putNextEntry(new ZipEntry(resourceDirectoryPath));

                    String downLoadPath = batchConversionActualityPath + resourceDirectoryPath;
                    File downLoadDirectory = new File(downLoadPath);

                    //将文件夹内的文件逐个写入ZIP
                    File[] files = downLoadDirectory.listFiles();
                    assert files != null;
                    for (File file : files) {
                        //将files存储在zipEntryPath下，如果传"",就直接下载到压缩包最外层目录下。
                        FileInfoServiceImpl.addFileToZip(resourceDirectoryPath, file, zos);

                    }
                    //完成当前压缩包的写入
                    zos.closeEntry();
                }

                //关闭zip输出流流
                zos.close();

                //单个资源下载情况
            } else {
                //创建zip输出流
                ServletOutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os);

                BatchConversion batchConversion = batchConversionMapper.selectById(ids[0]);

                //待下载的文件目录路径
                String resourceDirectoryName = batchConversion.getResourceDirectoryName();
                //创建压缩包条目-就是在压缩包里面新建目录,将此目录作为待下载的文件存储地方
                String resourceDirectoryPath = batchConversionActualityPath + resourceDirectoryName + "/";
                File downLoadDirectory = new File(resourceDirectoryPath);
                //构建压缩包包名,由于待下载目录名太长，取上传时的压缩包名字
                String uploadFileName = batchConversion.getUploadFileName();
                String downFileName = uploadFileName.substring(0,uploadFileName.lastIndexOf('.')) + FileSuffixConstants._ZIP;

                //设置响应头
                response.setHeader("Content-Disposition", "attachment;fileName=" +
                        URLEncoder.encode(downFileName, StandardCharsets.UTF_8));

                //将文件夹内的文件逐个写入ZIP
                File[] files = downLoadDirectory.listFiles();
                assert files != null;
                for (File file : files) {
                    FileInfoServiceImpl.addFileToZip("", file, zos);
                }
                //完成当前压缩包的写入
                zos.closeEntry();
                //关闭流
                zos.close();
            }

        } catch (Exception e) {
            log.error("批量下载转换文件失败:{}", e.getMessage());
        }
    }

    @Override
    public Boolean deleteById(Long id) {
        return batchConversionMapper.deleteById(id) == 1;
    }

}
