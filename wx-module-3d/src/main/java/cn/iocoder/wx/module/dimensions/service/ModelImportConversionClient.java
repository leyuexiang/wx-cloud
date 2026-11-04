package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import okhttp3.*;
import okio.BufferedSink;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Proxy;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode.*;

/** 仅传输冻结ZIP与目标格式；临时产物在确认长度后才能上传对象存储。 */
@Component
class ModelImportConversionClient {
    private static final long MAX_ARCHIVE_BYTES=2L*1024*1024*1024;
    private static final MediaType ZIP=MediaType.get("application/zip");
    private static final int BUFFER_BYTES=64*1024;
    private final HttpUrl url;
    private final OkHttpClient http;
    private final long archiveLimit;

    ModelImportConversionClient(@Value("${dim3d.model-import.converter-url:}") String url,
                                @Value("${dim3d.model-import.converter-timeout-ms:960000}") long timeoutMs,
                                @Value("${dim3d.model-import.converter-max-archive-bytes:2147483648}") long archiveLimit) {
        HttpUrl parsed=HttpUrl.parse(url);
        this.url=parsed!=null && parsed.username().isEmpty() && parsed.password().isEmpty()
                && parsed.query()==null && parsed.fragment()==null?parsed:null;
        if(timeoutMs<1 || timeoutMs>Integer.MAX_VALUE || archiveLimit<4 || archiveLimit>MAX_ARCHIVE_BYTES)
            throw new IllegalArgumentException("模型转换时限或产物大小配置超出范围");
        this.archiveLimit=archiveLimit;
        // 复用现有对象存储客户端的HTTP库；总时限覆盖上传、工具执行和持续下载。
        this.http=new OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .readTimeout(timeoutMs,TimeUnit.MILLISECONDS).callTimeout(timeoutMs,TimeUnit.MILLISECONDS).build();
    }

    boolean configured(){return url!=null;}

    Archive convert(InputStream source,long sourceSize,String format,BooleanSupplier active) {
        if(!configured())throw new Failure(CONVERTER_UNAVAILABLE);
        if(!("gltf".equals(format) || "glb".equals(format)) || sourceSize<4)throw new Failure(UNSUPPORTED_INPUT);
        requireActive(active);
        var body=new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("format",format)
                .addFormDataPart("file","source.zip",new FrozenBody(source,sourceSize,active)).build();
        // 多段请求体不会传递子段的一次性标记；外层显式禁止复用已消耗的源流。
        var oneShot=new RequestBody() {
            @Override public MediaType contentType(){return body.contentType();}
            @Override public long contentLength() throws IOException {return body.contentLength();}
            @Override public boolean isOneShot(){return true;}
            @Override public void writeTo(BufferedSink sink) throws IOException {body.writeTo(sink);}
        };
        var request=new Request.Builder().url(url).header("Accept","application/zip").post(oneShot).build();
        try(Response response=http.newCall(request).execute()) {
            int status=response.code();
            if(status==429)throw new Failure(CONVERTER_BUSY);
            if(status==408 || status==504)throw new Failure(CONVERTER_TIMEOUT);
            if(status==400 || status==413 || status==415 || status==422)throw new Failure(UNSUPPORTED_INPUT);
            if(status!=200)throw new Failure(CONVERTER_UNAVAILABLE);
            // 兼容转换器的HTTP 200业务失败；不读取或回传工具诊断。
            var result=response.body();
            var type=result==null?null:result.contentType();
            if(type==null || !"application".equalsIgnoreCase(type.type()) || !"zip".equalsIgnoreCase(type.subtype()))
                throw new Failure(INVALID_MODEL);
            if(result.contentLength()>archiveLimit)throw new Failure(INVALID_MODEL);
            return spool(result.byteStream(),active);
        }catch(SourceReadException unavailable){throw new Failure(SOURCE_STORAGE_UNAVAILABLE);}
        catch(InterruptedIOException timeout){throw new Failure(CONVERTER_TIMEOUT);}
        catch(IOException unavailable){throw new Failure(CONVERTER_UNAVAILABLE);}
    }

    private Archive spool(InputStream input,BooleanSupplier active) throws IOException {
        byte[] magic=input.readNBytes(4);
        // 此处仅识别传输结果；完整ZIP、路径和模型闭包由VALIDATING阶段独立复核。
        if(magic.length!=4 || magic[0]!='P' || magic[1]!='K' || magic[2]!=3 || magic[3]!=4)
            throw new Failure(INVALID_MODEL);
        Path file;
        try {file=Files.createTempFile("model-import-",".zip");}
        catch(IOException ex){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
        boolean complete=false;
        try {
            var digest=sha256();long length=magic.length;
            try(OutputStream output=localOutput(file)) {
                writeLocal(output,magic,magic.length);digest.update(magic);
                byte[] buffer=new byte[BUFFER_BYTES];int read;
                while((read=input.read(buffer))!=-1) {
                    requireActive(active);
                    length+=read;if(length>archiveLimit)throw new Failure(INVALID_MODEL);
                    writeLocal(output,buffer,read);digest.update(buffer,0,read);
                }
                requireActive(active);
            }
            var archive=new Archive(file,length,HexFormat.of().formatHex(digest.digest()));
            complete=true;return archive;
        }finally {if(!complete)deleteTemporary(file);}
    }

    private static OutputStream localOutput(Path file) {
        try {
            return new FilterOutputStream(Files.newOutputStream(file)) {
                @Override public void write(byte[] bytes,int offset,int length) throws IOException {out.write(bytes,offset,length);}
                @Override public void close() {
                    try {super.close();}catch(IOException ex){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
                }
            };
        }catch(IOException ex){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
    }

    private static void writeLocal(OutputStream output,byte[] bytes,int length) {
        try {output.write(bytes,0,length);}catch(IOException ex){throw new Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
    }

    private static MessageDigest sha256() {
        try {return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }

    private static void requireActive(BooleanSupplier active) {
        if(!active.getAsBoolean())throw new Failure(CONVERTER_UNAVAILABLE);
    }

    private static final class FrozenBody extends RequestBody {
        private final InputStream source;
        private final long size;
        private final BooleanSupplier active;
        FrozenBody(InputStream source,long size,BooleanSupplier active){this.source=source;this.size=size;this.active=active;}
        @Override public MediaType contentType(){return ZIP;}
        @Override public long contentLength(){return size;}
        @Override public void writeTo(BufferedSink sink) throws IOException {
            var buffer=new byte[BUFFER_BYTES];long remaining=size;
            while(remaining>0) {
                requireActive(active);
                int read;
                try {read=source.read(buffer,0,(int)Math.min(remaining,buffer.length));}
                catch(IOException ex){throw new SourceReadException();}
                if(read<0)throw new SourceReadException();
                sink.write(buffer,0,read);remaining-=read;
            }
        }
    }

    private static final class SourceReadException extends IOException {}

    record Archive(Path file,long size,String sha256) implements AutoCloseable {
        @Override public void close(){deleteTemporary(file);}
    }

    private static void deleteTemporary(Path file) {
        try {Files.deleteIfExists(file);}
        catch(IOException ex){LoggerFactory.getLogger(ModelImportConversionClient.class).warn("模型转换临时产物清理失败");}
    }

    static final class Failure extends RuntimeException {
        private final ModelImportErrorCode error;
        Failure(ModelImportErrorCode error){super(error.getMsg());this.error=error;}
        ModelImportErrorCode error(){return error;}
    }
}
