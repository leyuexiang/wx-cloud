package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** 使用已定版的含中文跨语言夹具验证服务端算法，不复制实现生成期望值。 */
class ArtResourceManifestVerifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ArtResourceManifestVerifier verifier = new ArtResourceManifestVerifier();
    private ObjectNode fixture;
    @BeforeEach void load() throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null && !Files.exists(directory.resolve("contracts/art01/content-hash-fixture.json"))) directory = directory.getParent();
        assertNotNull(directory, "应能定位版本化摘要合同夹具");
        fixture = (ObjectNode) mapper.readTree(directory.resolve("contracts/art01/content-hash-fixture.json").toFile());
    }
    private ObjectNode manifest() { return ((ObjectNode) fixture.get("manifest")).deepCopy(); }
    private ArtResourceManifestVerifier.Verified verify(ObjectNode manifest) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(manifest);
        return verifier.verify(bytes, fixture.path("manifest").path("resourceId").asText(), ArtResourceManifestVerifier.sha256(bytes));
    }
    @Test void canonicalDigestMatchesIndependentFixture() throws Exception {
        var result = verify(manifest());
        assertEquals(fixture.get("contentHash").asText(), result.contentHash());
        assertEquals(4, result.files().size());
        assertEquals(10, result.totalBytes());
        assertThrows(UnsupportedOperationException.class, () -> result.files().clear());
    }
    @Test void fieldAndFileOrderDoesNotChangeContentIdentity() throws Exception {
        ObjectNode root = manifest();
        ArrayNode original = (ArrayNode) root.get("files");
        ArrayNode reversed = mapper.createArrayNode();
        for (int i = original.size() - 1; i >= 0; i--) reversed.add(original.get(i));
        root.set("files", reversed);
        assertEquals(fixture.get("contentHash").asText(), verify(root).contentHash());
    }
    @Test void renamedManifestCannotIncludeItself() throws Exception {
        ObjectNode root=manifest();
        String file=root.get("files").get(0).get("path").asText();
        byte[] bytes=mapper.writeValueAsBytes(root);
        assertThrows(ServiceException.class,()->verifier.verify(bytes,root.get("resourceId").asText(),ArtResourceManifestVerifier.sha256(bytes),file));
    }
    @Test void resourceIdCannotBeRebound() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(manifest());
        assertThrows(ServiceException.class, () -> verifier.verify(bytes, "1", ArtResourceManifestVerifier.sha256(bytes)));
    }
    @Test void manifestByteDigestMustMatch() throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(manifest());
        assertThrows(ServiceException.class, () -> verifier.verify(bytes, manifest().get("resourceId").asText(), "a".repeat(64)));
    }
    @Test void strictJsonRejectsDuplicateTrailingBomAndMalformedUtf8() {
        for (String value : List.of("{\"x\":1,\"x\":2}", "{} {}", "\ufeff{}"))
            assertThrows(ServiceException.class, () -> verifier.parse(value.getBytes(StandardCharsets.UTF_8)));
        assertThrows(ServiceException.class, () -> verifier.parse(new byte[]{(byte)0xc3, (byte)0x28}));
    }
    @Test void largeManifestRejectedBeforeParsing() {
        byte[] large = new byte[ArtResourceManifestVerifier.MAX_MANIFEST_BYTES + 1];
        assertThrows(ServiceException.class, () -> verifier.verify(large, "1", "a".repeat(64)));
    }
    @TestFactory Stream<DynamicTest> malformedManifestRejected() {
        Map<String, Consumer<ObjectNode>> cases = new LinkedHashMap<>();
        cases.put("未知根字段", n -> n.put("projectId", "1"));
        cases.put("缺少字段", n -> n.remove("entry"));
        cases.put("错误协议", n -> n.put("schemaVersion", "unknown"));
        cases.put("错误平台", n -> n.put("platform", "WebGPU"));
        cases.put("错误格式", n -> n.put("contentFormat", "glb"));
        cases.put("数字ID", n -> n.put("resourceId", 1));
        cases.put("ID溢出", n -> n.put("resourceId", "9223372036854775808"));
        cases.put("不兼容身份空白", n -> n.put("appCompatibility", "a b"));
        cases.put("未知类型", n -> ((ObjectNode)n.get("entry")).put("kind", "UNKNOWN"));
        cases.put("入口悬空", n -> ((ObjectNode)n.get("entry")).put("packagePath", "missing.bundle"));
        cases.put("未知入口字段", n -> ((ObjectNode)n.get("entry")).put("url", "https://evil.invalid"));
        cases.put("空文件集", n -> ((ArrayNode)n.get("files")).removeAll());
        cases.put("重复文件", n -> ((ArrayNode)n.get("files")).add(n.get("files").get(0).deepCopy()));
        cases.put("主清单自引用", n -> ((ObjectNode)n.get("files").get(0)).put("path", "manifest.json"));
        cases.put("数字文件大小", n -> ((ObjectNode)n.get("files").get(0)).put("size", 1));
        cases.put("零大小", n -> ((ObjectNode)n.get("files").get(0)).put("size", "0"));
        cases.put("单文件超限", n -> ((ObjectNode)n.get("files").get(0)).put("size", "2147483649"));
        cases.put("大写摘要", n -> ((ObjectNode)n.get("files").get(0)).put("sha256", "A".repeat(64)));
        cases.put("错误内容摘要", n -> n.put("contentHash", "a".repeat(64)));
        cases.put("孤立代理字符", n -> ((ObjectNode)n.get("entry")).put("assetName", "\ud800"));
        cases.put("总容量超限", n -> { for (JsonNode f : n.get("files")) ((ObjectNode)f).put("size", "2147483648"); });
        return cases.entrySet().stream().map(item -> DynamicTest.dynamicTest(item.getKey(), () -> {
            ObjectNode root = manifest(); item.getValue().accept(root);
            assertThrows(ServiceException.class, () -> verify(root));
        }));
    }
    @TestFactory Stream<DynamicTest> unsafePathsRejected() {
        return List.of("../x", "a/../b", "/x", "a//x", "a/./x", "a\\x", "%2e%2e/x", "https://evil.invalid/x", "a?b", "a#b", "a/", "").stream()
                .map(value -> DynamicTest.dynamicTest("拒绝路径:" + value, () -> assertThrows(ServiceException.class,
                        () -> ArtResourceManifestVerifier.requirePath(value))));
    }
    @Test void objectKeyCannotEscapeResourceStorage() {
        assertEquals("resources/staging/a/manifest.json", ArtResourceObjectStore.objectKey("resources/staging/a/", "manifest.json"));
        assertThrows(ServiceException.class, () -> ArtResourceObjectStore.objectKey("other/", "manifest.json"));
        assertThrows(ServiceException.class, () -> ArtResourceObjectStore.objectKey("resources/a/../../", "manifest.json"));
    }
}
