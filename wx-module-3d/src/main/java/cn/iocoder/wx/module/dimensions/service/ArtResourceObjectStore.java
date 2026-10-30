package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import io.minio.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/**
 * 暂存对象回读和独立前缀冻结。客户端只获得暂存写入能力，永远不获得冻结前缀的PUT签名。
 * 冻结失败只留下待回收的孤立对象，不覆盖旧资源，也不在此组件修改数据库指针。
 */
@Component
public class ArtResourceObjectStore {
    @Resource(name = "3dMinioClient") private MinioClient minio;
    @Resource private MinioConfigInfo config;
    private static final int BUFFER_BYTES = 64 * 1024;

    public byte[] readManifest(String prefix, String path) {
        String key = objectKey(prefix, path);
        try (InputStream input = open(key)) {
            byte[] bytes = input.readNBytes(ArtResourceManifestVerifier.MAX_MANIFEST_BYTES + 1);
            if (bytes.length > ArtResourceManifestVerifier.MAX_MANIFEST_BYTES) throw ServiceException.of(INVALID_CLOSURE);
            return bytes;
        } catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw ServiceException.of(OBJECT_MISSING); }
    }

    /**
     * 先验证暂存完整对象，再流式写独立对象，最后验证冻结对象本身。
     * 不使用只复制元数据的ETag，也不把大包或完整闭包读入JVM内存。
     */
    public void freeze(String stagingPrefix, String finalPrefix, List<ArtResourceManifestVerifier.FileSpec> files) {
        if (Objects.equals(stagingPrefix, finalPrefix) || files == null || files.isEmpty()
                || files.size() > ArtResourceManifestVerifier.MAX_FILES + 1) throw ServiceException.of(INVALID_CLOSURE);
        Set<String> paths = new HashSet<>();
        long total = 0;
        // 所有键和配额先校验，避免处理到中途才发现非法请求。
        for (var file : files) {
            objectKey(stagingPrefix, file.path()); objectKey(finalPrefix, file.path());
            if (!paths.add(file.path()) || file.size() <= 0 || file.size() > ArtResourceManifestVerifier.MAX_FILE_BYTES
                    || !ArtResourceManifestVerifier.digest(file.sha256())) throw ServiceException.of(INVALID_CLOSURE);
            total += file.size();
            if (total > ArtResourceManifestVerifier.MAX_CLOSURE_BYTES + ArtResourceManifestVerifier.MAX_MANIFEST_BYTES)
                throw ServiceException.of(INVALID_CLOSURE);
        }
        for (var file : files) {
            String source = objectKey(stagingPrefix, file.path());
            String destination = objectKey(finalPrefix, file.path());
            verifyObject(source, file);
            // 最终前缀由提交服务逐次分配，已有对象即拒绝，避免错误重试覆盖已验证内容。
            if (exists(destination)) throw ServiceException.of(IDEMPOTENCY_CONFLICT);
            try (InputStream input = open(source)) {
                write(destination, input, file.size());
            } catch (Exception ex) { throw ServiceException.of(STORAGE_UNAVAILABLE); }
            // 暂存可能在两次读取间被改写，校验冻结字节可阻止混入未经验证内容。
            verifyObject(destination, file);
        }
    }

    /** 原生包基础格式门禁；真正Unity加载能力仍由兼容Player/生产工具验证。 */
    public void verifyUnityHeader(String prefix,String path) {
        try(InputStream input=open(objectKey(prefix,path))){
            byte[] magic=input.readNBytes(8);
            if(!java.util.Arrays.equals(magic,new byte[]{85,110,105,116,121,70,83,0}))
                throw ServiceException.of(INVALID_CLOSURE);
        }catch(ServiceException ex){throw ex;}catch(Exception ex){throw ServiceException.of(OBJECT_MISSING);}
    }
    public void verifyObject(String object, ArtResourceManifestVerifier.FileSpec file) {
        try {
            if (size(object) != file.size()) throw ServiceException.of(CHECKSUM_MISMATCH);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            byte[] buffer = new byte[BUFFER_BYTES];
            try (InputStream input = open(object)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > file.size()) throw ServiceException.of(CHECKSUM_MISMATCH);
                    digest.update(buffer, 0, read);
                }
            }
            if (count != file.size() || !HexFormat.of().formatHex(digest.digest()).equals(file.sha256()))
                throw ServiceException.of(CHECKSUM_MISMATCH);
        } catch (ServiceException ex) { throw ex; }
        catch (io.minio.errors.ErrorResponseException ex) {
            if (Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(ex.errorResponse().code()))
                throw ServiceException.of(OBJECT_MISSING);
            throw ServiceException.of(STORAGE_UNAVAILABLE);
        } catch (Exception ex) { throw ServiceException.of(STORAGE_UNAVAILABLE); }
    }
    protected boolean exists(String object) {
        try {
            minio.statObject(StatObjectArgs.builder().bucket(config.getBucketName()).object(object).build());
            return true;
        } catch (io.minio.errors.ErrorResponseException ex) {
            if (Set.of("NoSuchKey", "NoSuchObject").contains(ex.errorResponse().code())) return false;
            throw ServiceException.of(STORAGE_UNAVAILABLE);
        } catch (Exception ex) { throw ServiceException.of(STORAGE_UNAVAILABLE); }
    }
    /** 可替换的存储边界，测试可模拟远程对象在两次读取之间变化。 */
    protected InputStream open(String key) throws Exception {
        return minio.getObject(GetObjectArgs.builder().bucket(config.getBucketName()).object(key).build());
    }
    protected long size(String key) throws Exception {
        return minio.statObject(StatObjectArgs.builder().bucket(config.getBucketName()).object(key).build()).size();
    }
    protected void write(String key, InputStream input, long size) throws Exception {
        minio.putObject(PutObjectArgs.builder().bucket(config.getBucketName()).object(key)
                .stream(input, size, -1).contentType("application/octet-stream").build());
    }
    public static String objectKey(String prefix, String path) {
        if (prefix == null || !prefix.startsWith("resources/") || !prefix.endsWith("/"))
            throw ServiceException.of(UNSAFE_PATH);
        ArtResourceManifestVerifier.requirePath(prefix.substring(0, prefix.length() - 1));
        ArtResourceManifestVerifier.requirePath(path);
        String key = prefix + path;
        if (key.length() > 1024) throw ServiceException.of(UNSAFE_PATH);
        return key;
    }
}
