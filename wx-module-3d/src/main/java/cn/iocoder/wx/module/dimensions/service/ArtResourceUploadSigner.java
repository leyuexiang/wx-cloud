package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 只签发暂存键，最终内容不授予客户端写权限；摘要/大小由已绑定计划在服务端校验。 */
@Component
public class ArtResourceUploadSigner {
    @Resource private MinioConfigInfo storage;
    @Value("${dim3d.resource.upload-endpoint:}") private String endpoint;
    public String sign(String prefix,String path) {
        try {
            URI uri=URI.create(endpoint);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                || uri.getQuery()!=null || uri.getFragment()!=null || !SetPath.root(uri.getPath())) throw ServiceException.of(STORAGE_UNAVAILABLE);
            if(!prefix.startsWith("resources/staging/")) throw ServiceException.of(UNSAFE_PATH);
            String key=ArtResourceObjectStore.objectKey(prefix,path);
            var client=MinioClient.builder().endpoint(endpoint).region("us-east-1")
                .credentials(storage.getAccessKey(),storage.getSecretKey()).build();
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT)
                .bucket(storage.getBucketName()).object(key).expiry(300,TimeUnit.SECONDS).build());
        } catch(ServiceException ex){throw ex;}catch(Exception ex){throw ServiceException.of(STORAGE_UNAVAILABLE);}
    }
    private static class SetPath {static boolean root(String path){return path.isEmpty() || path.equals("/");}}
}
