package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import io.minio.errors.ErrorResponseException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import static cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode.*;
import static cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier.*;

/** 只读取与导入操作绑定的固定 ZIP，按实际读取字节复核，再交给格式校验。 */
@Component
class ModelImportArtifactValidation {
    private static final Set<String> MISSING_CODES=Set.of("NoSuchKey","NoSuchObject","NoSuchBucket");
    private final ArtResourceObjectStore store;
    private final ModelImportArtifactVerifier verifier;

    ModelImportArtifactValidation(ArtResourceObjectStore store,ModelImportArtifactVerifier verifier) {
        this.store=store;
        this.verifier=verifier;
    }

    ModelImportArtifactVerifier.Verified validate(String operation,String format,JsonNode descriptor,BooleanSupplier active) {
        return withArchive(operation,format,descriptor,active,file->{
            try {return verifier.verify(file,format,active);}
            catch(ServiceException invalid){throw new Failure(INVALID_MODEL);}
        });
    }

    /** 同一个冻结对象读取边界供校验与发布复用，回调退出后清理临时文件。 */
    <T>T withArchive(String operation,String format,JsonNode descriptor,BooleanSupplier active,Function<Path,T> work) {
        String prefix="resources/model-imports/"+operation+"/";
        String key=descriptor.path("key").asText("");
        String bytes=descriptor.path("size").asText("");
        String digest=descriptor.path("sha256").asText("");
        if(!key.startsWith(prefix) || !key.substring(prefix.length()).matches("[0-9a-f-]{36}/converted\\.zip") ||
                !bytes.matches("[1-9][0-9]{0,9}") || !digest(digest) || !format.equals(descriptor.path("format").asText()))
            throw new Failure(ARTIFACT_UNAVAILABLE);
        long size=Long.parseLong(bytes);
        if(size>MAX_FILE_BYTES)throw new Failure(ARTIFACT_UNAVAILABLE);

        Path file;
        try {file=Files.createTempFile("model-validation-",".zip");}
        catch(IOException unavailable){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
        try {
            download(key,size,digest,file,active);
            return work.apply(file);
        } finally {
            try {Files.deleteIfExists(file);}
            catch(IOException unavailable){LoggerFactory.getLogger(getClass()).warn("模型校验临时产物清理失败");}
        }
    }

    private void download(String key,long expectedSize,String expectedDigest,Path file,BooleanSupplier active) {
        var digest=ModelImportArtifactVerifier.sha256Digest();
        long size=0;
        byte[] buffer=new byte[64*1024];
        try(InputStream input=store.open(key);OutputStream output=Files.newOutputStream(file)) {
            for(int read;(read=input.read(buffer))!=-1;) {
                ModelImportArtifactVerifier.checkActive(active);
                size+=read;
                if(size>expectedSize)throw new Failure(ARTIFACT_UNAVAILABLE);
                digest.update(buffer,0,read);
                output.write(buffer,0,read);
            }
        } catch(Failure | CancellationException failure){throw failure;}
        catch(ErrorResponseException error){
            throw new Failure(MISSING_CODES.contains(error.errorResponse().code())?ARTIFACT_UNAVAILABLE:ARTIFACT_STORAGE_UNAVAILABLE);
        } catch(Exception unavailable){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
        if(size!=expectedSize || !HexFormat.of().formatHex(digest.digest()).equals(expectedDigest))
            throw new Failure(ARTIFACT_UNAVAILABLE);
    }

    static final class Failure extends RuntimeException {
        private final ModelImportErrorCode code;
        Failure(ModelImportErrorCode code){super(code.getMsg());this.code=code;}
        ModelImportErrorCode code(){return code;}
    }
}
