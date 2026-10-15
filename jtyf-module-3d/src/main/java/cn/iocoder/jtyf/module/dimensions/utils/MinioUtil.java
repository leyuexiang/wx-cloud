package cn.iocoder.jtyf.module.dimensions.utils;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.jtyf.framework.common.exception.ConditionException;
import cn.iocoder.jtyf.module.dimensions.config.CustomMinioClient;
import cn.iocoder.jtyf.module.dimensions.config.MinioConfigInfo;
import cn.iocoder.jtyf.module.dimensions.domain.dto.FileInfoBase;
import cn.iocoder.jtyf.module.dimensions.domain.model.FileUploadInfo;
import cn.iocoder.jtyf.module.dimensions.domain.model.UploadUrlsVO;
import cn.iocoder.jtyf.framework.common.enums.HttpCodeEnum;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MinioFileVO;
import cn.iocoder.jtyf.module.dimensions.service.constant.FileSuffixConstants;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.HashMultimap;
import io.minio.*;
import io.minio.errors.*;

import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.minio.messages.Part;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dromara.hutool.core.data.id.IdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author : akon
 * @date : 2024/11/06
 * @desc: Minio工具类
 */
@Slf4j
@Component
public class MinioUtil {

    @Autowired
    @Qualifier("3dMinioClient")
    private MinioClient minioClient;

    private CustomMinioClient customMinioClient;

    @Resource
    private MinioConfigInfo minioConfigInfo;

    /**
     * 验证bucketName是否存在
     *
     * @return boolean true:存在
     */
    public boolean bucketExists(String bucketName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    public void createBucket(String bucketName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 获取存储桶策略
     *
     * @param bucketName 存储桶名称
     * @return json
     */
    private JSONObject getBucketPolicy(String bucketName) throws ServerException, InsufficientDataException, ErrorResponseException, BucketPolicyTooLargeException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        String bucketPolicy = minioClient.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucketName).build());
        return JSONObject.parseObject(bucketPolicy);
    }

    /**
     * 获取全部bucket
     * <p>
     * <a href="https://docs.minio.io/cn/java-client-api-reference.html#listBuckets">...</a>
     */
    public List<Bucket> getAllBuckets() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.listBuckets();
    }

    /**
     * 根据bucketName获取信息
     *
     * @param bucketName bucket名称
     */
    public Optional<Bucket> getBucket(String bucketName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return minioClient.listBuckets().stream().filter(b -> b.name().equals(bucketName)).findFirst();
    }

    /**
     * 根据bucketName删除信息
     *
     * @param bucketName bucket名称
     */
    public void removeBucket(String bucketName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
    }


    /**
     * 判断文件是否存在
     *
     * @param bucketName 存储桶
     * @param objectName 对象
     * @return true：存在
     */
    public boolean doesObjectExist(String bucketName, String objectName) {
        boolean exist = true;
        try {
            statObject(bucketName, objectName);
        } catch (Exception e) {
            exist = false;
        }
        return exist;
    }

    /**
     * 判断文件夹是否存在
     *
     * @param bucketName 存储桶
     * @param objectName 文件夹名称（去掉/）
     * @return true：存在
     */
    public boolean doesFolderExist(String bucketName, String objectName) {
        boolean exist = false;
        try {
            Iterable<Result<Item>> results = listObjects(bucketName, objectName, false);
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() && objectName.equals(item.objectName())) {
                    exist = true;
                }
            }
        } catch (Exception e) {
            exist = false;
        }
        return exist;
    }

    /**
     * 根据文件前置查询文件
     *
     * @param bucketName bucket名称
     * @param prefix     前缀
     * @param recursive  是否递归查询
     * @return MinioItem 列表
     */
    public List<Item> getAllObjectsByPrefix(String bucketName, String prefix, boolean recursive) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        List<Item> list = new ArrayList<>();
        Iterable<Result<Item>> objectsIterator = listObjects(bucketName, prefix, recursive);
        if (objectsIterator != null) {
            for (Result<Item> o : objectsIterator) {
                Item item = o.get();
                list.add(item);
            }
        }
        return list;
    }

    /**
     * 获取文件流
     *
     * @param bucketName bucket名称
     * @param filePath 文件名称
     * @return 二进制流
     */
    public InputStream download(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return getObject(bucketName, filePath);
    }

    /**
     * 批量下载文件，将多个文件压缩打包zip
     *
     * @param minioFileVOList 文件位置&名称信息VO
     * @return 二进制流
     */
    public void downloadZipFile(List<MinioFileVO> minioFileVOList, HttpServletResponse response)
            throws ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, IOException, InvalidResponseException, XmlParserException, InternalException {
        byte[] buf = new byte[1024];
        try (BufferedOutputStream bos = new BufferedOutputStream(response.getOutputStream())) {
            response.setHeader("Content-Disposition", "attachment;filename=" + IdWorker.getId() + FileSuffixConstants._ZIP);
            response.setContentType("application/octet-stream");
            response.setCharacterEncoding(CharsetUtil.UTF_8);

            // 当文件名有重复的时候，增加文件名后缀数字
            Map<String, Integer> fileNameNumMap = new HashMap<>(minioFileVOList.size());

            // ZipOutputStream类：完成文件或文件夹的压缩
            ZipOutputStream out = new ZipOutputStream(bos);
            for (MinioFileVO minioFileVO : minioFileVOList) {
                GetObjectResponse objectResponse = getObject(minioFileVO.getBucket(), minioFileVO.getObjectName());
                // 给列表中的文件单独命名
                String fileName = getCurrentFileName(minioFileVO.getObjectName(), fileNameNumMap);
                out.putNextEntry(new ZipEntry(fileName));
                int len;
                while ((len = objectResponse.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.closeEntry();
                objectResponse.close();
            }
            out.close();
        }
    }

    private String getCurrentFileName(String filePath, Map<String, Integer> fileNameNumMap) {
        // 给列表中的文件单独命名
        String fileName = StringUtils.substringAfterLast(filePath, "/");
        Integer count = fileNameNumMap.get(fileName);
        if (count == null) {
            fileNameNumMap.put(fileName, 1);
            return fileName;
        }
        // count数量代表文件名重复数，例如：1.jpg, 1(1).jpg, 1(2).jpg；有重复则增加名称 + 数字
        fileNameNumMap.put(fileName, count + 1);
        return FileUtils.getFileName(fileName) + "(" + count + ")." + FileUtils.getFileSuffix(fileName);
    }

    public GetObjectResponse getObject(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return getObject(bucketName, filePath, null, null);
    }

    /**
     * 断点下载
     *
     * @param bucketName bucket名称
     * @param filePath 文件名称
     * @param offset     起始字节的位置
     * @param length     要读取的长度
     * @return 流
     */
    public GetObjectResponse getObject(String bucketName, String filePath, Long offset, Long length) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        GetObjectArgs.Builder builder = GetObjectArgs.builder().bucket(bucketName).object(objectName);
        if (Objects.nonNull(offset)) {
            builder.offset(offset);
        }
        if (Objects.nonNull(length)) {
            builder.length(length);
        }
        return minioClient.getObject(builder.build());
    }

    public StatObjectResponse statObject(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        return minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
    }

    /**
     * 获取路径下文件列表
     *
     * @param bucketName bucket名称
     * @param prefix     文件名称
     * @param recursive  是否递归查找，如果是false,就模拟文件夹结构查找
     * @return 二进制流
     */
    public Iterable<Result<Item>> listObjects(String bucketName, String prefix, boolean recursive) {
        ListObjectsArgs.Builder builder = ListObjectsArgs.builder().bucket(bucketName).recursive(recursive);
        if (Objects.nonNull(prefix)) {
            // 确保目录路径以斜杠结尾
            if (!prefix.endsWith("/")) {
                prefix = prefix + "/";
            }
            // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
            builder.prefix(prefix.replace("\\","/"));
        }
        return minioClient.listObjects(builder.build());
    }

    /**
     * 列出一个桶中的所有文件和目录
     */
    public List<FileInfoBase> listFiles(String bucket) {
        Iterable<Result<Item>> results = listObjects(bucket, null, true);

        List<FileInfoBase> infos = new ArrayList<>();
        results.forEach(r -> {
            FileInfoBase info = new FileInfoBase();
            try {
                Item item = r.get();
                info.setFilename(item.objectName());
                info.setDirectory(item.isDir());
                infos.add(info);
            } catch (Exception e) {
                log.error("列出一个桶中的所有文件和目录失败！error:{}", e.getMessage(), e);
            }
        });
        return infos;
    }

    /**
     * 通过MultipartFile，上传文件
     *
     * @param bucketName 存储桶
     * @param file       文件
     * @param filePath 对象名
     */
    public ObjectWriteResponse uploadFile(String bucketName, MultipartFile file, String filePath, String contentType) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return uploadFile(bucketName, filePath, file.getInputStream(), contentType);
    }

    /**
     * 上传本地文件
     *
     * @param bucketName 存储桶
     * @param filePath 文件对象路径
     * @param srcFilePath   本地文件路径
     */
    public ObjectWriteResponse uploadFile(String bucketName, String filePath, String srcFilePath) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        return minioClient.uploadObject(UploadObjectArgs.builder().bucket(bucketName).object(objectName).filename(srcFilePath).build());
    }

    /**
     * 通过流上传文件
     *
     * @param bucketName  存储桶
     * @param filePath  文件对象路径
     * @param inputStream 文件流
     */
    public ObjectWriteResponse uploadFile(String bucketName, String filePath, InputStream inputStream) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return uploadFile(bucketName, filePath, inputStream, null);
    }

    public ObjectWriteResponse uploadFile(String bucketName, String filePath, InputStream inputStream, String contentType) throws IOException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(inputStream, inputStream.available(), -1);
        if (Objects.nonNull(contentType)) {
            builder.contentType(contentType);
        }
        return minioClient.putObject(builder.build());
    }

    /**
     * 图片上传
     *
     * @param bucketName
     * @param imageBase64
     * @param imageName
     * @return
     */
    public ObjectWriteResponse uploadImage(String bucketName, String imageBase64, String imageName) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        if (!StrUtil.isEmpty(imageBase64)) {
            InputStream in = base64ToInputStream(imageBase64);
            String newName = System.currentTimeMillis() + "_" + imageName + FileSuffixConstants._JPG;
            String year = String.valueOf(Calendar.YEAR);
            String month = String.valueOf(Calendar.MONTH);
            return uploadFile(bucketName, year + "/" + month + "/" + newName, in);
        }
        return null;
    }

    private static InputStream base64ToInputStream(String base64) {
        ByteArrayInputStream stream = null;
        try {
            byte[] bytes = Base64Decoder.decode(base64.trim().getBytes());
            stream = new ByteArrayInputStream(bytes);
        } catch (Exception e) {
            log.error("图片上传失败！error:{}", e.getMessage(),e);
        }
        return stream;
    }

    /**
     * 创建文件夹或目录
     *
     * @param bucketName 存储桶
     * @param filePath 目录路径
     */
    public ObjectWriteResponse createDir(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        return minioClient.putObject(PutObjectArgs
                .builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(new ByteArrayInputStream(new byte[]{}), 0, -1)
                .build()
        );
    }

    /**
     * 获取文件信息, 如果抛出异常则说明文件不存在
     *
     * @param bucketName bucket名称
     * @param filePath 文件名称
     */
    public StatObjectResponse getObjectInfo(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return statObject(bucketName, filePath);
    }

    /**
     * 拷贝文件
     *
     * @param srcBucketName    bucket名称
     * @param srcFilePath    文件名称
     * @param destBucketName 目标bucket名称
     * @param destFilePath 目标文件名称
     */
    public void copyObject(String srcBucketName, String srcFilePath, String destBucketName, String destFilePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String srcObjectName = srcFilePath.replace("\\","/");
        String destObjectName = destFilePath.replace("\\","/");
        minioClient.copyObject(CopyObjectArgs.builder().source(CopySource.builder().bucket(srcBucketName).object(srcObjectName).build()).bucket(destBucketName).object(destObjectName).build());
    }

    /**
     * 删除一个桶
     */
    public void deleteBucket(String bucket) throws Exception {
        minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
    }

    /**
     * 删除一个对象
     */
    public void deleteObject(String bucket, String filePath) throws Exception {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    /**
     * 批量删除文件
     *
     * @param bucketName bucket
     * @param keys       需要删除的文件列表
     * @return
     */
    public void deleteObjects(String bucketName, List<String> keys) {
        List<DeleteObject> objects = new LinkedList<>();
        keys.forEach(s -> {
            objects.add(new DeleteObject(s));
            try {
                deleteObject(bucketName, s);
            } catch (Exception e) {
                log.error("批量删除失败！error:{}",e.getMessage(), e);
            }
        });
    }

    /**
     * 获取文件外链
     *
     * @param bucketName 存储桶
     * @param filePath 文件名
     * @param expires    过期时间(外链有效时间（单位：秒））,expires秒后到期；默认为7天
     * @return url
     */
    public String getPresignedObjectUrl(String bucketName, String filePath, Integer expires) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
        String objectName = filePath.replace("\\","/");
        GetPresignedObjectUrlArgs.Builder builder = GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName).method(Method.GET);
        if (Objects.nonNull(expires)) {
            builder.expiry(expires);
        }
        return minioClient.getPresignedObjectUrl(builder.build());
    }

    /**
     * 获得文件外链
     *
     * @param bucketName
     * @param filePath
     * @return url
     */
    public String getPresignedObjectUrl(String bucketName, String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        return getPresignedObjectUrl(bucketName, filePath, null);
    }

    /**
     * 获取minio中所有的文件
     *
     * @Param: []
     * @return: java.util.List<boot.spring.domain.Fileinfo>
     */
    public List<FileInfoBase> listAllFile() throws Exception {
        List<Bucket> list = this.getAllBuckets();
        List<FileInfoBase> fileInfos = new ArrayList<>();
        for (Bucket bucketName : list) {
            fileInfos.addAll(this.listFiles(bucketName.name()));
        }


        return fileInfos;
    }

    /**
     * 将URLDecoder编码转成UTF8
     *
     * @param str
     * @return
     */
    public String getUtf8ByURLDecoder(String str) {
        String url = str.replaceAll("%(?![0-9a-fA-F]{2})", "%25");
        return URLDecoder.decode(url, StandardCharsets.UTF_8);
    }


    /**
     * 列出指定目录下的所有文件
     * @param bucketName 存储桶名称
     * @param directoryPath 目录路径
     * @param recursive 是否递归列出子目录下的文件
     * @throws IOException 输入输出异常
     * @throws NoSuchAlgorithmException 算法异常
     * @throws InvalidKeyException 密钥异常
     */
    public List<String> listFilesInDirectory(String bucketName, String directoryPath, boolean recursive) throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException, ServerException, InsufficientDataException, ErrorResponseException, InvalidResponseException, XmlParserException, InternalException {
        // 列出对象
        Iterable<Result<Item>> results = listObjects(bucketName, directoryPath, recursive);
        // 遍历结果
        List<String> fileList = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            if (!item.isDir()) {
                fileList.add(item.objectName());
            }
        }
        return fileList;
    }

    /**
     * 获取 Minio 中已经上传的分片文件
     * @param object 文件名称
     * @param uploadId 上传的文件id（由 minio 生成）
     * @return List<Integer>
     */
    @SneakyThrows
    public List<Integer> getListParts(String object, String uploadId) {
        List<Part> parts = getParts(object, uploadId);
        return parts.stream()
                .map(Part::partNumber)
                .collect(Collectors.toList());
    }

    /**
     * 单文件签名上传
     * @param filePath 文件名称（uuid 格式）
     * @return UploadUrlsVO
     */
    public UploadUrlsVO getUploadObjectUrl(String contentType, String filePath) {
        try {
            log.info("<{}> 开始单文件上传<minio>", filePath);
            UploadUrlsVO urlsVO = new UploadUrlsVO();
            List<String> urlList = new ArrayList<>();
            // 主要是针对图片，若需要通过浏览器直接查看，而不是下载，需要指定对应的 content-type
            HashMultimap<String, String> headers = HashMultimap.create();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);

            String uploadId = IdUtil.simpleUUID();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);

            // Minio存储的路径规范推荐使用正斜杠（/），而不是反斜杠（\）。
            String objectName = filePath.replace("\\","/");
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(minioConfigInfo.getBucketName())
                    .object(objectName)
                    .extraHeaders(headers)
                    .extraQueryParams(reqParams)
                    .expiry(minioConfigInfo.getExpiry(), TimeUnit.DAYS)
                    .build());
            urlList.add(url);
            urlsVO.setUploadId(uploadId).setUrls(urlList);
            return urlsVO;
        } catch (Exception e) {
            log.error("单文件上传失败: {}", e.getMessage(),e);
            throw new ConditionException(HttpCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    /**
     * 初始化分片上传
     * @param fileUploadInfo 前端传入的文件信息
     * @param object object
     * @return UploadUrlsVO
     */
    public UploadUrlsVO initMultiPartUpload(FileUploadInfo fileUploadInfo, String object) {
        Integer chunkCount = fileUploadInfo.getChunkCount();
        String contentType = fileUploadInfo.getContentType();
        String uploadId = fileUploadInfo.getUploadId();

        log.info("文件<{}> - 分片<{}> 初始化分片上传数据 请求头 {}", object, chunkCount, contentType);
        UploadUrlsVO urlsVO = new UploadUrlsVO();
        try {
            HashMultimap<String, String> headers = HashMultimap.create();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);

            // 如果初始化时有 uploadId，说明是断点续传，不能重新生成 uploadId
            if (fileUploadInfo.getUploadId() == null || fileUploadInfo.getUploadId().isEmpty()) {
                uploadId = customMinioClient.initMultiPartUpload(minioConfigInfo.getBucketName(), null, object, headers, null);
            }
            urlsVO.setUploadId(uploadId);

            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= chunkCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl = customMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(minioConfigInfo.getBucketName())
                        .object(object)
                        .expiry(1, TimeUnit.DAYS)
                        .extraQueryParams(reqParams)
                        .build());
                partList.add(uploadUrl);
            }

            log.info("文件初始化分片成功");
            urlsVO.setUrls(partList);
            return urlsVO;
        } catch (Exception e) {
            log.error("初始化分片上传失败: {}", e.getMessage());
            // 返回 文件上传失败
            throw new ConditionException(HttpCodeEnum.UPLOAD_FILE_FAILED);
        }
    }

    /**
     * 合并文件
     * @param object object
     * @param uploadId uploadUd
     */
    @SneakyThrows
    public boolean mergeMultipartUpload(String object, String uploadId) {
        log.info("通过 <{}-{}-{}> 合并<分片上传>数据", object, uploadId, minioConfigInfo.getBucketName());
        // 获取所有分片
        List<Part> partsList = getParts(object, uploadId);
        Part[] parts = new Part[partsList.size()];
        int partNumber = 1;
        for (Part part : partsList) {
            parts[partNumber - 1] = new Part(partNumber, part.etag());
            partNumber++;
        }
        // 合并分片
        customMinioClient.mergeMultipartUpload(minioConfigInfo.getBucketName(), null, object, uploadId, parts, null, null);
        return true;
    }

    /**
     * 获取文件内容和元信息，该文件不存在会抛异常
     * @param object object
     * @return StatObjectResponse
     */
    @SneakyThrows
    public StatObjectResponse statObject(String object) {
        return customMinioClient.statObject(StatObjectArgs.builder()
                        .bucket(minioConfigInfo.getBucketName())
                        .object(object)
                        .build())
                .get();
    }

    @SneakyThrows
    public GetObjectResponse getObject(String object, Long offset, Long contentLength) {
        return customMinioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfigInfo.getBucketName())
                        .object(object)
                        .offset(offset)
                        .length(contentLength)
                        .build())
                .get();
    }


    @NotNull
    private  List<Part> getParts(String object, String uploadId) throws Exception {
        int partNumberMarker = 0;
        boolean isTruncated = true;
        List<Part> parts = new ArrayList<>();
        while(isTruncated){
            ListPartsResponse partResult = customMinioClient.listMultipart(minioConfigInfo.getBucketName(), null, object, 1000, partNumberMarker, uploadId, null, null);
            parts.addAll(partResult.result().partList());
            // 检查是否还有更多分片
            isTruncated = partResult.result().isTruncated();
            if (isTruncated) {
                // 更新partNumberMarker以获取下一页的分片数据
                partNumberMarker = partResult.result().nextPartNumberMarker();
            }
        }
        return parts;
    }
}