package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.enums.HttpCodeEnum;
import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.framework.common.util.json.JsonUtils;
import cn.iocoder.jtyf.framework.common.util.object.BeanUtils;
import cn.iocoder.jtyf.module.dimensions.config.MinioConfigInfo;
import cn.iocoder.jtyf.module.dimensions.domain.model.FileUploadInfo;
import cn.iocoder.jtyf.module.dimensions.domain.model.UploadUrlsVO;
import cn.iocoder.jtyf.module.dimensions.entity.FilesMinio;
import cn.iocoder.jtyf.module.dimensions.mapper.FilesMinioMapper;
import cn.iocoder.jtyf.module.dimensions.service.IFilesMinioService;
import cn.iocoder.jtyf.module.dimensions.utils.MinioUtil;
import cn.iocoder.jtyf.module.dimensions.utils.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.minio.GetObjectResponse;
import io.minio.StatObjectResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文件表 服务实现类
 */
@Slf4j
@Service
public class FilesMinioServiceImpl extends ServiceImpl<FilesMinioMapper, FilesMinio> implements IFilesMinioService {
    private static final Integer BUFFER_SIZE = 1024 * 64; // 64KB

    @Resource
    private RedisUtil redisUtil;
    @Resource
    private MinioUtil minioUtil;
    @Resource
    private MinioConfigInfo minioConfigInfo;
    @Resource
    private FilesMinioMapper filesMinioMapper;

    @Override
    public FileUploadInfo checkFileByMd5(String md5) {
        log.info("查询md5: <{}> 在redis是否存在", md5);
        FileUploadInfo fileUploadInfo = (FileUploadInfo) redisUtil.get(md5);
        if (fileUploadInfo != null) {
            List<Integer> listParts = minioUtil.getListParts(fileUploadInfo.getObject(), fileUploadInfo.getUploadId());
            fileUploadInfo.setListParts(listParts);
            throw JtyfBusinessException.of(HttpCodeEnum.UPLOADING.getCode(), JsonUtils.toJsonString(fileUploadInfo));

        }
        log.info("redis中不存在md5: <{}> 查询mysql是否存在", md5);
        FilesMinio file = filesMinioMapper.selectOne(new LambdaQueryWrapper<FilesMinio>().eq(FilesMinio::getMd5, md5));
        if (file != null) {
            log.info("mysql中存在md5: <{}> 的文件 该文件已上传至minio 秒传直接过", md5);
            FileUploadInfo dbFileInfo = BeanUtils.toBean(file, FileUploadInfo.class);
            return dbFileInfo;
        }
        throw JtyfBusinessException.of(HttpCodeEnum.NOT_UPLOADED.getCode(), HttpCodeEnum.NOT_UPLOADED.getMsg());
    }

    @Override
    public UploadUrlsVO initMultipartUpload(FileUploadInfo fileUploadInfo) {
        FileUploadInfo redisFileUploadInfo = (FileUploadInfo)redisUtil.get(fileUploadInfo.getMd5());
        // 若 redis 中有该 md5 的记录，以 redis 中为主
        String object;
        if (redisFileUploadInfo != null) {
            fileUploadInfo = redisFileUploadInfo;
            object = redisFileUploadInfo.getObject();
        } else {
            String originFileName = fileUploadInfo.getOriginFileName();
            String suffix = FileUtil.extName(originFileName);
            String fileName = FileUtil.mainName(originFileName);
            // 对文件重命名，并以年月日文件夹格式存储
            String nestFile = DateUtil.format(LocalDateTime.now(), "yyyy/MM/dd");
            object = nestFile + "/" + fileName + "_" + fileUploadInfo.getMd5() + "." + suffix;

            fileUploadInfo.setObject(object).setType(suffix);
        }

        UploadUrlsVO urlsVO;
        // 单文件上传
        if (fileUploadInfo.getChunkCount() == 1) {
            log.info("当前分片数量 <{}> 单文件上传", fileUploadInfo.getChunkCount());
            urlsVO = minioUtil.getUploadObjectUrl(fileUploadInfo.getContentType(), object);
        } else {
            // 分片上传
            log.info("当前分片数量 <{}> 分片上传", fileUploadInfo.getChunkCount());
            urlsVO = minioUtil.initMultiPartUpload(fileUploadInfo, object);
        }
        fileUploadInfo.setUploadId(urlsVO.getUploadId());

        // 存入 redis （单片存 redis 唯一用处就是可以让单片也入库，因为单片只有一个请求，基本不会出现问题）
        redisUtil.set(fileUploadInfo.getMd5(), fileUploadInfo, minioConfigInfo.getBreakpointTime(), TimeUnit.DAYS);
        return urlsVO;
    }

    @Override
    public String mergeMultipartUpload(String md5) {
        FileUploadInfo redisFileUploadInfo = (FileUploadInfo)redisUtil.get(md5);

        String url = StrUtil.format("{}/{}/{}", minioConfigInfo.getUrl(), minioConfigInfo.getBucketName(), redisFileUploadInfo.getObject());
        FilesMinio files = BeanUtils.toBean(redisFileUploadInfo, FilesMinio.class);
        files.setUrl(url)
                .setBucket(minioConfigInfo.getBucketName())
                .setCreateTime(LocalDateTime.now());

        Integer chunkCount = redisFileUploadInfo.getChunkCount();
        // 分片为 1 ，不需要合并，否则合并后看返回的是 true 还是 false
        boolean isSuccess = chunkCount == 1 || minioUtil.mergeMultipartUpload(redisFileUploadInfo.getObject(), redisFileUploadInfo.getUploadId());
        if (isSuccess) {
            filesMinioMapper.insert(files);
            redisUtil.del(md5);
            return url;
        }
        throw JtyfBusinessException.of(HttpCodeEnum.UPLOAD_FILE_FAILED.getCode(), HttpCodeEnum.UPLOAD_FILE_FAILED.getMsg());
    }

    @Override
    public ResponseEntity<byte[]> downloadMultipartFile(Long id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        // redis 缓存当前文件信息，避免分片下载时频繁查库
        FilesMinio file = null;
        FilesMinio redisFile = (FilesMinio)redisUtil.get(String.valueOf(id));
        if (redisFile == null) {
            FilesMinio dbFile = filesMinioMapper.selectById(id);
            if (dbFile == null) {
                return null;
            } else {
                file = dbFile;
                redisUtil.set(String.valueOf(id), file, 1, TimeUnit.DAYS);
            }
        } else {
            file = redisFile;
        }

        String range = request.getHeader("Range");
        String fileName = file.getOriginFileName();
        log.info("下载文件的 object <{}>", file.getObject());
        // 获取 bucket 桶中的文件元信息，获取不到会抛出异常
        StatObjectResponse objectResponse = minioUtil.statObject(file.getObject());
        long startByte = 0; // 开始下载位置
        long fileSize = objectResponse.size();
        long endByte = fileSize - 1; // 结束下载位置
        log.info("文件总长度：{}，当前 range：{}", fileSize, range);

        BufferedOutputStream os = null; // buffer 写入流
        GetObjectResponse stream = null; // minio 文件流

        // 存在 range，需要根据前端下载长度进行下载，即分段下载
        // 例如：range=bytes=0-52428800
        if (range != null && range.contains("bytes=") && range.contains("-")) {
            range = range.substring(range.lastIndexOf("=") + 1).trim(); // 0-52428800
            String[] ranges = range.split("-");
            // 判断range的类型
            if (ranges.length == 1) {
                // 类型一：bytes=-2343 后端转换为 0-2343
                if (range.startsWith("-")) endByte = Long.parseLong(ranges[0]);
                // 类型二：bytes=2343- 后端转换为 2343-最后
                if (range.endsWith("-")) startByte = Long.parseLong(ranges[0]);
            } else if (ranges.length == 2) { // 类型三：bytes=22-2343
                startByte = Long.parseLong(ranges[0]);
                endByte = Long.parseLong(ranges[1]);
            }
        }

        // 要下载的长度
        // 确保返回的 contentLength 不会超过文件的实际剩余大小
        long contentLength = Math.min(endByte - startByte + 1, fileSize - startByte);
        // 文件类型
        String contentType = request.getServletContext().getMimeType(fileName);

        // 解决下载文件时文件名乱码问题
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
        fileName = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);

        // 响应头设置---------------------------------------------------------------------------------------------
        // 断点续传，获取部分字节内容：
        response.setHeader("Accept-Ranges", "bytes");
        // http状态码要为206：表示获取部分内容,SC_PARTIAL_CONTENT,若部分浏览器不支持，改成 SC_OK
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setContentType(contentType);
        response.setHeader("Last-Modified", objectResponse.lastModified().toString());
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        response.setHeader("Content-Length", String.valueOf(contentLength));
        // Content-Range，格式为：[要下载的开始位置]-[结束位置]/[文件总大小]
        response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + objectResponse.size());
        response.setHeader("ETag", "\"".concat(objectResponse.etag()).concat("\""));
        response.setContentType("application/octet-stream;charset=UTF-8");

        try {
            // 获取文件流
            stream = minioUtil.getObject(objectResponse.object(), startByte, contentLength);
            os = new BufferedOutputStream(response.getOutputStream());
            // 将读取的文件写入到 OutputStream
            byte[] bytes = new byte[BUFFER_SIZE];
            long bytesWritten = 0;
            int bytesRead = -1;
            while ((bytesRead = stream.read(bytes)) != -1) {
                if (bytesWritten + bytesRead >= contentLength) {
                    os.write(bytes, 0, (int)(contentLength - bytesWritten));
                    break;
                } else {
                    os.write(bytes, 0, bytesRead);
                    bytesWritten += bytesRead;
                }
            }
            os.flush();
            response.flushBuffer();
            // 返回对应http状态
            return new ResponseEntity<>(bytes, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (os != null) os.close();
            if (stream != null) stream.close();
        }
        return null;
    }

    @Override
    public List<FilesMinio> getFileList() {
        return filesMinioMapper.selectList(null);
    }
}
