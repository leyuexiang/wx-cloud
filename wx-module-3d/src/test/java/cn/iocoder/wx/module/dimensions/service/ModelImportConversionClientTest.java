package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** 真实环回HTTP验证现有转换器的请求和响应合同，不依赖转换工具或云存储。 */
class ModelImportConversionClientTest {
    HttpServer server;
    String url;

    @BeforeEach void start() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.start();url="http://127.0.0.1:"+server.getAddress().getPort()+"/convert";
    }
    @AfterEach void stop(){server.stop(0);}

    @Test void streamsMultipartAndSpoolsOnlyZipWithDigest() throws Exception {
        byte[] zip=zip();var received=new AtomicReference<String>();var auth=new AtomicReference<String>();
        server.createContext("/convert",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.ISO_8859_1));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type","application/zip");
            exchange.sendResponseHeaders(200,zip.length);
            try(var out=exchange.getResponseBody()){out.write(zip);}
        });
        java.nio.file.Path file;
        try(var result=new ModelImportConversionClient(url,3000,4096).convert(new ByteArrayInputStream(zip),zip.length,"glb",()->true)) {
            file=result.file();
            assertArrayEquals(zip,Files.readAllBytes(file));assertEquals(zip.length,result.size());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)),result.sha256());
        }
        assertFalse(Files.exists(file));assertNull(auth.get());
        assertTrue(received.get().contains("name=\"file\"; filename=\"source.zip\""));
        assertTrue(received.get().contains("name=\"format\"") && received.get().contains("\r\n\r\nglb\r\n"));
        assertTrue(received.get().contains(new String(zip,StandardCharsets.ISO_8859_1)));
    }

    @Test void legacyJsonIsFailureWithoutLeakingToolDiagnostics(){
        reply(200,"application/json","{\"code\":\"CONVERT_FAIL\",\"message\":\"private-host-path\"}".getBytes(StandardCharsets.UTF_8),false);
        assertFailure(ModelImportErrorCode.INVALID_MODEL,3000,4096);
    }
    @Test void busyHasDedicatedError(){reply(429,"application/json",new byte[]{1},false);assertFailure(ModelImportErrorCode.CONVERTER_BUSY,3000,4096);}
    @Test void serverTimeoutHasDedicatedError(){reply(504,"application/json",new byte[]{1},false);assertFailure(ModelImportErrorCode.CONVERTER_TIMEOUT,3000,4096);}
    @Test void unsupportedInputHasDedicatedError(){reply(415,"application/json",new byte[]{1},false);assertFailure(ModelImportErrorCode.UNSUPPORTED_INPUT,3000,4096);}
    @Test void rejectsFakeZip(){reply(200,"application/zip",new byte[]{1,2,3,4},false);assertFailure(ModelImportErrorCode.INVALID_MODEL,3000,4096);}
    @Test void rejectsOversizedChunkedZip() throws Exception {reply(200,"application/zip",zip(),true);assertFailure(ModelImportErrorCode.INVALID_MODEL,3000,16);}
    @Test void redirectIsNeverFollowed(){
        server.createContext("/convert",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Location",url+"/elsewhere");
            exchange.sendResponseHeaders(307,-1);exchange.close();
        });
        assertFailure(ModelImportErrorCode.CONVERTER_UNAVAILABLE,3000,4096);
    }
    @Test void converterCannotTriggerReplayOfConsumedSource(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/convert",exchange->{
            calls.incrementAndGet();exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Retry-After","0");
            exchange.sendResponseHeaders(503,-1);exchange.close();
        });
        assertFailure(ModelImportErrorCode.CONVERTER_UNAVAILABLE,3000,4096);
        assertEquals(1,calls.get());
    }
    @Test void truncatedSourceIsNotReportedAsConverterOutage(){
        var client=new ModelImportConversionClient(url,3000,4096);
        var failure=assertThrows(ModelImportConversionClient.Failure.class,()->client.convert(new ByteArrayInputStream(new byte[]{1}),4,"gltf",()->true));
        assertEquals(ModelImportErrorCode.SOURCE_STORAGE_UNAVAILABLE,failure.error());
    }
    @Test void missingConverterConfigDoesNotAcceptSource(){
        var client=new ModelImportConversionClient("",3000,4096);
        var failure=assertThrows(ModelImportConversionClient.Failure.class,()->client.convert(new ByteArrayInputStream(new byte[0]),4,"glb",()->true));
        assertEquals(ModelImportErrorCode.CONVERTER_UNAVAILABLE,failure.error());
    }
    @Test void timeoutAlsoBoundsAStalledResponseBody(){
        var release=new CountDownLatch(1);var headers=new CountDownLatch(1);
        server.createContext("/convert",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type","application/zip");
            exchange.sendResponseHeaders(200,0);headers.countDown();
            try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}finally{exchange.close();}
        });
        try {assertFailure(ModelImportErrorCode.CONVERTER_TIMEOUT,1000,4096);assertEquals(0,headers.getCount());}
        finally {release.countDown();}
    }
    private void assertFailure(ModelImportErrorCode expected,long timeout,long limit){
        var client=new ModelImportConversionClient(url,timeout,limit);
        var failure=assertThrows(ModelImportConversionClient.Failure.class,()->client.convert(new ByteArrayInputStream(new byte[]{1,2,3,4}),4,"gltf",()->true));
        assertEquals(expected,failure.error());assertFalse(failure.getMessage().contains("private-host-path"));
    }
    private void reply(int status,String type,byte[] bytes,boolean chunked) {
        server.createContext("/convert",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.getResponseHeaders().set("Content-Type",type);
            exchange.sendResponseHeaders(status,chunked?0:bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
    }
    private static byte[] zip() throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("sample.glb"));zip.write(new byte[]{1,2,3,4});}
        return bytes.toByteArray();
    }
}
