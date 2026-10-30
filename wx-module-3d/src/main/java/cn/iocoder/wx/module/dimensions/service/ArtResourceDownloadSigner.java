package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import io.minio.MinioClient;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.Resource;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 下载签名在范围鉴权后才生成；无可信HTTPS入口时明确失败，不退回公开HTTP地址。 */
@Component
public class ArtResourceDownloadSigner {
    @Resource private MinioConfigInfo storage;
    @Value("${dim3d.resource.download-endpoint:}") private String endpoint;
    @Value("${dim3d.resource.download-region:us-east-1}") private String region;
    public Map<String, String> sign(String prefix, String path) {
        try {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) throw ServiceException.of(STORAGE_UNAVAILABLE);
            if (!safe(prefix) || !safe(path) || path.endsWith("/")) throw ServiceException.of(UNSAFE_PATH);
            String key = prefix + (prefix.endsWith("/") ? "" : "/") + path;
            // 不调用存储发现接口；签名区域来自受信配置，避免为签名新增网络查询。
            MinioClient client = MinioClient.builder().endpoint(endpoint).region(region)
                .credentials(storage.getAccessKey(), storage.getSecretKey()).build();
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                .bucket(storage.getBucketName()).object(key).expiry(300, TimeUnit.SECONDS).build());
            return Map.of("manifestUrl", url, "downloadExpiresAt", Instant.now().plusSeconds(300).toString());
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw ServiceException.of(STORAGE_UNAVAILABLE); }
    }
    private boolean safe(String path) {
        return path != null && path.length() <= 512 && path.matches("[A-Za-z0-9_./-]+")
            && !path.startsWith("/") && !path.contains("//")
            && java.util.Arrays.stream(path.split("/")).noneMatch(p -> p.equals(".") || p.equals(".."));
    }
}
