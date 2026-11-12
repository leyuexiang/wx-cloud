package evaluation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.dromara.x.file.storage.core.platform.FileStorageClientFactory;
import org.dromara.x.file.storage.core.platform.MinioFileStorage;
import org.dromara.x.file.storage.core.recorder.DefaultFileRecorder;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.dromara.x.file.storage.spring.EnableFileStorage;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 固定版本兼容性探针：S3 服务为本地协议替身，不代表真实 MinIO 验收。 */
@Timeout(30)
class XFileStorageCompatibilityTest {
    private S3Stub stub;
    private FileStorageService service;
    private MemoryRecorder recorder;
    private OkHttpClient http;

    @Configuration(proxyBeanMethods = false)
    @EnableFileStorage
    static class ProbeConfiguration {}

    @BeforeEach
    void start() throws IOException {
        stub = new S3Stub();
        http = new OkHttpClient();
        MinioClient client = MinioClient.builder().endpoint(stub.endpoint()).region("us-east-1")
                // 仅本地协议替身使用的固定假凭据，不连接任何真实环境。
                .credentials("probe-access", "probe-secret-only-for-local-stub").httpClient(http).build();
        FileStorageProperties.MinioConfig config = new FileStorageProperties.MinioConfig();
        config.setPlatform("probe-minio");
        config.setBucketName("probe-bucket");
        config.setBasePath("managed-files/");
        config.setDomain(stub.endpoint() + "/probe-bucket/");
        config.setMultipartThreshold(5 * 1024 * 1024);
        config.setMultipartPartSize(5 * 1024 * 1024);
        FileStorageClientFactory<MinioClient> factory = new FileStorageClientFactory<>() {
            public String getPlatform() { return "probe-minio"; }
            public MinioClient getClient() { return client; }
        };
        recorder = new MemoryRecorder();
        service = FileStorageServiceBuilder.create(new FileStorageProperties().setDefaultPlatform("probe-minio"))
                .useDefault().setFileRecorder(recorder).addFileStorage(new MinioFileStorage(config, factory)).build();
    }

    @AfterEach
    void close() {
        if (service != null) service.destroy();
        if (http != null) {
            http.connectionPool().evictAll();
            http.dispatcher().executorService().shutdownNow();
        }
        if (stub != null) stub.close();
    }

    @Test
    void springBoot334AndJakartaContextStarts() {
        new ApplicationContextRunner().withUserConfiguration(ProbeConfiguration.class)
                .withPropertyValues("dromara.x-file-storage.default-platform=unused")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(FileStorageService.class));
                    assertInstanceOf(DefaultFileRecorder.class, context.getBean(FileRecorder.class));
                    assertEquals("3.3.4", org.springframework.boot.SpringBootVersion.getVersion());
                    assertNotNull(jakarta.servlet.http.HttpServletRequest.class);
                    assertThrows(ClassNotFoundException.class, () -> Class.forName("javax.servlet.http.HttpServletRequest"));
                });
    }

    @Test
    void defaultRecorderDoesNotPersistIdentity() {
        DefaultFileRecorder fallback = new DefaultFileRecorder();
        FileInfo file = new FileInfo();
        assertTrue(fallback.save(file));
        assertNull(file.getId());
        assertThrows(RuntimeException.class, () -> fallback.getByUrl("probe-only"));
    }

    @Test
    void knownLengthStreamClosesAndRoundTripsDigest() throws Exception {
        byte[] content = "普通文件独立兼容性探针".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream input = new TrackingInputStream(content);
        FileInfo file = service.of(input, "probe.bin", "application/octet-stream", (long) content.length)
                .setPath("frozen/test/").setSaveFilename("payload").setHashCalculatorSha256().upload();
        assertTrue(input.closed);
        assertEquals((long) content.length, file.getSize());
        assertEquals(hex(content), file.getHashInfo().getSha256());
        assertNotNull(file.getId());
        byte[][] downloaded = new byte[1][];
        service.download(file).inputStream(in -> {
            try { downloaded[0] = in.readAllBytes(); } catch (IOException e) { throw new UncheckedIOException(e); }
        });
        assertArrayEquals(content, downloaded[0]);
    }

    @Test
    void multipartFileAdapterLoadsOnSpring6() {
        new ApplicationContextRunner().withUserConfiguration(ProbeConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure());
            FileStorageService springService = context.getBean(FileStorageService.class);
            var upload = springService.of(new MockMultipartFile("file", "probe.txt", "text/plain", new byte[]{65}));
            assertEquals("probe.txt", upload.getOriginalFilename());
        });
    }

    @Test
    void automaticMultipartWorksWithMinio857() throws Exception {
        byte[] content = new byte[6 * 1024 * 1024 + 19];
        Arrays.fill(content, (byte) 31);
        FileInfo file = service.of(new ByteArrayInputStream(content), "large.bin", "application/octet-stream", (long) content.length)
                .setPath("staging/auto/").setSaveFilename("payload").setHashCalculatorSha256().upload();
        assertEquals(hex(content), file.getHashInfo().getSha256());
        assertArrayEquals(content, stub.objects.get("/probe-bucket/managed-files/staging/auto/payload"));
        assertTrue(stub.partUploads.get() >= 2);
    }

    @Test
    void reflectiveManualMultipartCanListCompleteAndAbort() {
        byte[] payload = "manual-part".getBytes(StandardCharsets.UTF_8);
        FileInfo file = service.initiateMultipartUpload().setPath("staging/manual/")
                .setSaveFilename("payload").setContentType("application/octet-stream").setSize((long) payload.length).init();
        assertNotNull(file.getUploadId());
        FilePartInfo part = service.uploadPart(file, 1, payload, (long) payload.length).upload();
        assertEquals(payload.length, part.getPartSize());
        var parts = service.listParts(file).listParts();
        assertEquals(1, parts.getList().size());
        service.completeMultipartUpload(file).setPartInfoList(parts.getList()).complete();
        assertArrayEquals(payload, stub.objects.get("/probe-bucket/managed-files/staging/manual/payload"));
        FileInfo cancelled = service.initiateMultipartUpload().setPath("staging/cancel/")
                .setSaveFilename("payload").setContentType("application/octet-stream").init();
        service.abortMultipartUpload(cancelled).abort();
        assertFalse(stub.parts.containsKey(cancelled.getUploadId()));
    }

    @Test
    void presigningIsLocalWithFixedRegionAndHonoursExpiry() {
        int before = stub.requests.get();
        var signed = service.generatePresignedUrl().setPlatform("probe-minio").setPath("frozen/test/")
                .setFilename("payload").setMethod("GET").setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .generatePresignedUrl();
        URI uri = URI.create(signed.getUrl());
        Map<String, String> query = S3Stub.query(uri);
        assertTrue(Integer.parseInt(query.get("X-Amz-Expires")) <= 60);
        assertTrue(Integer.parseInt(query.get("X-Amz-Expires")) > 0);
        assertEquals(before, stub.requests.get());
        assertThrows(RuntimeException.class, () -> service.generatePresignedUrl().setPlatform("probe-minio")
                .setPath("frozen/test/").setFilename("payload").setMethod("GET")
                .setExpiration(Date.from(Instant.now().minusSeconds(5))).generatePresignedUrl());
        // 签名地址只在断言内使用，不输出到证据或日志。
    }

    @Test
    void recorderFailureLeavesUncommittedPhysicalObject() {
        recorder.failSave = true;
        assertThrows(RuntimeException.class, () -> service.of(new byte[]{1, 2, 3}, "fail.bin", "application/octet-stream")
                .setPath("staging/db-failure/").setSaveFilename("payload").upload());
        assertTrue(stub.objects.containsKey("/probe-bucket/managed-files/staging/db-failure/payload"));
    }

    @Test
    void directDeleteRemovesObjectBeforeRecorderFailure() {
        FileInfo file = service.of(new byte[]{1}, "delete.bin", "application/octet-stream")
                .setPath("frozen/delete/").setSaveFilename("payload").upload();
        recorder.failDelete = true;
        assertThrows(RuntimeException.class, () -> service.delete(file));
        assertFalse(stub.objects.containsKey("/probe-bucket/managed-files/frozen/delete/payload"));
        assertTrue(recorder.files.containsKey(file.getUrl()));
    }

    private static String hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;
        TrackingInputStream(byte[] bytes) { super(bytes); }
        public void close() throws IOException { closed = true; super.close(); }
    }

    /** 仅验证回调顺序和中断边界，明确不提供数据库一致性证明。 */
    private static class MemoryRecorder implements FileRecorder {
        final Map<String, FileInfo> files = new HashMap<>();
        boolean failSave, failDelete;
        public boolean save(FileInfo file) {
            if (failSave) throw new IllegalStateException("注入记录写入失败");
            file.setId(UUID.randomUUID().toString());
            files.put(file.getUrl(), file);
            return true;
        }
        public void update(FileInfo file) { files.put(file.getUrl(), file); }
        public FileInfo getByUrl(String url) { return files.get(url); }
        public boolean delete(String url) {
            if (failDelete) throw new IllegalStateException("注入记录删除失败");
            files.remove(url); return true;
        }
        public void saveFilePart(FilePartInfo part) {}
        public void deleteFilePartByUploadId(String uploadId) {}
    }

    /** 有限 S3 协议替身，不检查签名、桶策略、并发与持久性，不可冒充真实对象存储。 */
    private static class S3Stub implements AutoCloseable {
        final HttpServer server;
        final Map<String, byte[]> objects = new ConcurrentHashMap<>();
        final Map<String, SortedMap<Integer, byte[]>> parts = new ConcurrentHashMap<>();
        final AtomicInteger requests = new AtomicInteger(), partUploads = new AtomicInteger();
        S3Stub() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }
        String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        public void close() { server.stop(0); }
        static Map<String, String> query(URI uri) {
            Map<String, String> result = new HashMap<>();
            if (uri.getRawQuery() != null) for (String item : uri.getRawQuery().split("&")) {
                String[] pair = item.split("=", 2);
                result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
            }
            return result;
        }
        void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            try (exchange) {
                String method = exchange.getRequestMethod(), path = exchange.getRequestURI().getPath();
                Map<String, String> q = query(exchange.getRequestURI());
                byte[] body = exchange.getRequestBody().readAllBytes();
                String key = path.substring("/probe-bucket/".length());
                if (q.containsKey("location")) {
                    xml(exchange, "<LocationConstraint xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">us-east-1</LocationConstraint>");
                } else if (method.equals("POST") && q.containsKey("uploads")) {
                    String id = UUID.randomUUID().toString();
                    parts.put(id, new TreeMap<>());
                    xml(exchange, "<InitiateMultipartUploadResult><Bucket>probe-bucket</Bucket><Key>" + key + "</Key><UploadId>" + id + "</UploadId></InitiateMultipartUploadResult>");
                } else if (q.containsKey("uploadId")) {
                    String id = q.get("uploadId");
                    if (method.equals("PUT")) {
                        parts.get(id).put(Integer.parseInt(q.get("partNumber")), body);
                        partUploads.incrementAndGet();
                        exchange.getResponseHeaders().set("ETag", "\"probe-etag\"");
                        send(exchange, 200, new byte[0]);
                    } else if (method.equals("GET")) {
                        StringBuilder rows = new StringBuilder();
                        parts.get(id).forEach((number, data) -> rows.append("<Part><PartNumber>").append(number)
                                .append("</PartNumber><LastModified>2026-11-10T00:00:00.000Z</LastModified><ETag>probe-etag</ETag><Size>")
                                .append(data.length).append("</Size></Part>"));
                        xml(exchange, "<ListPartsResult><Bucket>probe-bucket</Bucket><Key>" + key + "</Key><UploadId>" + id
                                + "</UploadId><Initiator><ID>probe</ID><DisplayName>probe</DisplayName></Initiator>"
                                + "<Owner><ID>probe</ID><DisplayName>probe</DisplayName></Owner><StorageClass>STANDARD</StorageClass>"
                                + "<PartNumberMarker>0</PartNumberMarker><NextPartNumberMarker>1</NextPartNumberMarker><MaxParts>1000</MaxParts><IsTruncated>false</IsTruncated>" + rows + "</ListPartsResult>");
                    } else if (method.equals("POST")) {
                        ByteArrayOutputStream combined = new ByteArrayOutputStream();
                        for (byte[] data : parts.remove(id).values()) combined.write(data);
                        objects.put(path, combined.toByteArray());
                        xml(exchange, "<CompleteMultipartUploadResult><Location>" + endpoint() + path
                                + "</Location><Bucket>probe-bucket</Bucket><Key>" + key + "</Key><ETag>probe-etag</ETag></CompleteMultipartUploadResult>");
                    } else { parts.remove(id); send(exchange, 204, null); }
                } else if (method.equals("PUT")) {
                    objects.put(path, body);
                    exchange.getResponseHeaders().set("ETag", "\"probe-etag\"");
                    send(exchange, 200, new byte[0]);
                } else if (method.equals("DELETE")) {
                    objects.remove(path); send(exchange, 204, null);
                } else if (method.equals("GET")) {
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    send(exchange, objects.containsKey(path) ? 200 : 404, objects.get(path));
                } else {
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(objects.getOrDefault(path, new byte[0]).length));
                    exchange.getResponseHeaders().set("Last-Modified", "Tue, 10 Nov 2026 00:00:00 GMT");
                    exchange.getResponseHeaders().set("ETag", "\"probe-etag\"");
                    send(exchange, 200, null);
                }
            }
        }
        void xml(HttpExchange exchange, String text) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/xml");
            send(exchange, 200, text.getBytes(StandardCharsets.UTF_8));
        }
        void send(HttpExchange exchange, int status, byte[] bytes) throws IOException {
            exchange.sendResponseHeaders(status, bytes == null || bytes.length == 0 ? -1 : bytes.length);
            if (bytes != null && bytes.length > 0) exchange.getResponseBody().write(bytes);
        }
    }
}
