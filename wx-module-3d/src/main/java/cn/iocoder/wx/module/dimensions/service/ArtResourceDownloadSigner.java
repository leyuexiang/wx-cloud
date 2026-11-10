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
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
        return sign(prefix, path, 300, null);
    }

    /** 票据签发须受绝对到期时间约束，不能沿用普通管理下载的固定五分钟有效期。 */
    public Map<String, String> signUntil(String prefix, String path, Instant notAfter) {
        if (notAfter == null) throw new ServiceException(401, "访问票据已失效");
        long seconds = Math.min(300, Duration.between(Instant.now(), notAfter).getSeconds());
        if (seconds < 1) throw new ServiceException(401, "访问票据已失效");
        return sign(prefix, path, (int) seconds, notAfter);
    }

    private Map<String, String> sign(String prefix, String path, int seconds, Instant notAfter) {
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
            for (int attempt = 0; attempt < 2; attempt++) {
                // 初始化 SDK 后再计算剩余时间，并为跨秒留一秒余量；只允许一次本地重新签名。
                long remaining = notAfter == null ? seconds : Duration.between(Instant.now(), notAfter).getSeconds() - 1;
                if (remaining < 1) throw new ServiceException(401, "访问票据已失效");
                int ttl = (int) Math.min(seconds, remaining);
                String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                    .bucket(storage.getBucketName()).object(key).expiry(ttl, TimeUnit.SECONDS).build());
                // 从真实签名时间计算有效期，避免 SDK 时间取整或执行延迟使响应越过票据上限。
                Map<String, String> query = new java.util.HashMap<>();
                for (String pair : URI.create(url).getRawQuery().split("&")) {
                    String[] value = pair.split("=", 2);
                    query.put(value[0], URLDecoder.decode(value[1], StandardCharsets.UTF_8));
                }
                Instant signedAt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
                        .parse(query.get("X-Amz-Date"), Instant::from);
                Instant expires = signedAt.plusSeconds(Long.parseLong(query.get("X-Amz-Expires")));
                if (notAfter == null || !expires.isAfter(notAfter) && expires.isAfter(Instant.now()))
                    return Map.of("manifestUrl", url, "downloadExpiresAt", expires.toString());
            }
            throw new ServiceException(401, "访问票据已失效");
        } catch (ServiceException e) { throw e; }
        catch (Exception e) { throw ServiceException.of(STORAGE_UNAVAILABLE); }
    }
    private boolean safe(String path) {
        return path != null && path.length() <= 512 && path.matches("[A-Za-z0-9_./-]+")
            && !path.startsWith("/") && !path.contains("//")
            && java.util.Arrays.stream(path.split("/")).noneMatch(p -> p.equals(".") || p.equals(".."));
    }
}
