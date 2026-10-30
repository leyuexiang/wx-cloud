package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * 单资源主清单校验与跨语言内容摘要；不把结构合法等同于文件已上传或Unity可加载。
 * 此组件不读数据库、不变更指针，后续提交事务只接收其已验证结果。
 */
@Component
public class ArtResourceManifestVerifier {
    public static final int MAX_FILES = 4096;
    public static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long MAX_CLOSURE_BYTES = 4L * 1024 * 1024 * 1024;
    private final ObjectMapper parser = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> ROOT = Set.of("schemaVersion", "resourceId", "contentHash", "contentFormat", "platform", "appCompatibility", "entry", "files");
    private static final Set<String> ENTRY = Set.of("kind", "assetName", "packagePath", "nativeManifestPath", "catalogPath", "packManifestPath");
    private static final Set<String> KINDS = Set.of("MODEL", "SPRITE", "LIGHTING", "COMPOSITION");

    public record FileSpec(String path, long size, String sha256) {}
    public record Verified(String resourceId, String manifestSha256, String contentHash,
                           String compatibility, String kind, Map<String, String> entry,
                           List<FileSpec> files, long totalBytes) {}

    /** 服务端必须把目标资源ID与清单比对，避免把其它资源的包绑定到当前ID。 */
    public Verified verify(byte[] bytes, String expectedResourceId, String expectedManifestSha256) {
        return verify(bytes, expectedResourceId, expectedManifestSha256, "manifest.json");
    }

    /** 提交入口传入真实主清单路径，防止通过改名绕过自引用检查。 */
    public Verified verify(byte[] bytes, String expectedResourceId, String expectedManifestSha256, String manifestPath) {
        requirePath(manifestPath);
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_MANIFEST_BYTES) fail();
        String manifestHash = sha256(bytes);
        if (!digest(expectedManifestSha256) || !manifestHash.equals(expectedManifestSha256))
            throw ServiceException.of(ArtResourceErrorCode.CHECKSUM_MISMATCH);
        JsonNode root = parse(bytes);
        fields(root, ROOT, Set.of());
        if (!"wx.art-resource.v1".equals(text(root, "schemaVersion", 32))
                || !"unity.assetbundle.v1".equals(text(root, "contentFormat", 32))
                || !"WebGL".equals(text(root, "platform", 20))) fail();
        String resourceId = text(root, "resourceId", 19);
        if (!positiveId(resourceId) || !resourceId.equals(expectedResourceId)) fail();
        String compatibility = text(root, "appCompatibility", 128);
        if (compatibility.codePoints().anyMatch(Character::isWhitespace)) fail();
        String contentHash = text(root, "contentHash", 64);
        if (!digest(contentHash)) fail();
        JsonNode entryNode = root.get("entry");
        fields(entryNode, ENTRY, Set.of("lightingManifestPath"));
        String kind = text(entryNode, "kind", 20);
        if (!KINDS.contains(kind)) fail();
        text(entryNode, "assetName", 150);
        Map<String, String> entry = new TreeMap<>();
        entryNode.fields().forEachRemaining(value -> {
            String item = text(entryNode, value.getKey(), value.getKey().endsWith("Path") ? 512 : 150);
            if (value.getKey().endsWith("Path")) requirePath(item);
            entry.put(value.getKey(), item);
        });
        JsonNode fileArray = root.get("files");
        if (!fileArray.isArray() || fileArray.isEmpty() || fileArray.size() > MAX_FILES) fail();
        Map<String, FileSpec> files = new TreeMap<>();
        long total = 0;
        for (JsonNode node : fileArray) {
            fields(node, Set.of("path", "size", "sha256"), Set.of());
            String path = text(node, "path", 512);
            requirePath(path);
            // 主清单自身由数据库描述提供字节摘要，不允许自引用形成摘要环。
            if (path.equals(manifestPath)) fail();
            String sizeText = text(node, "size", 10);
            if (!sizeText.matches("[1-9][0-9]{0,9}")) fail();
            long size;
            try { size = Long.parseLong(sizeText); } catch (NumberFormatException ex) { fail(); return null; }
            if (size > MAX_FILE_BYTES) fail();
            total += size;
            if (total > MAX_CLOSURE_BYTES) fail();
            String hash = text(node, "sha256", 64);
            if (!digest(hash) || files.putIfAbsent(path, new FileSpec(path, size, hash)) != null) fail();
        }
        for (var item : entry.entrySet()) {
            if (item.getKey().endsWith("Path") && !files.containsKey(item.getValue())) fail();
        }
        if (!canonicalHash(root).equals(contentHash))
            throw ServiceException.of(ArtResourceErrorCode.CHECKSUM_MISMATCH);
        return new Verified(resourceId, manifestHash, contentHash, compatibility, kind,
                Map.copyOf(entry), List.copyOf(files.values()), total);
    }

    /** 严格UTF-8、重复字段和多根JSON都拒绝，不采用解析器默默覆盖字段的行为。 */
    public JsonNode parse(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_MANIFEST_BYTES) fail();
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\ufeff")) fail();
            return parser.readTree(text);
        } catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw ServiceException.of(ArtResourceErrorCode.INVALID_CLOSURE); }
    }

    /** ASCII字段键排序、文件路径排序、无BOM紧凑UTF-8，与ART-01夹具逐字节对齐。 */
    public String canonicalHash(JsonNode root) {
        try {
            Map<String, Object> payload = new TreeMap<>();
            root.fields().forEachRemaining(item -> {
                String key = item.getKey();
                if (key.equals("contentHash")) return;
                if (key.equals("entry")) {
                    Map<String, String> entry = new TreeMap<>();
                    item.getValue().fields().forEachRemaining(value -> entry.put(value.getKey(), value.getValue().asText()));
                    payload.put(key, entry);
                } else if (key.equals("files")) {
                    List<Map<String, String>> files = new ArrayList<>();
                    item.getValue().forEach(file -> files.add(new TreeMap<>(Map.of(
                            "path", file.path("path").asText(), "size", file.path("size").asText(), "sha256", file.path("sha256").asText()))));
                    files.sort(Comparator.comparing(file -> file.get("path")));
                    payload.put(key, files);
                } else payload.put(key, item.getValue().asText());
            });
            return sha256(parser.writeValueAsBytes(payload));
        } catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw ServiceException.of(ArtResourceErrorCode.INVALID_CLOSURE); }
    }

    /** 文件名限制成协议允许的相对ASCII路径，不做二次解码或归一化猜测。 */
    public static void requirePath(String path) {
        if (path == null || path.length() > 512 || path.isEmpty() || path.startsWith("/")
                || path.endsWith("/") || path.contains("//") || !path.matches("[A-Za-z0-9_./-]+"))
            throw ServiceException.of(ArtResourceErrorCode.UNSAFE_PATH);
        for (String part : path.split("/")) if (part.equals(".") || part.equals(".."))
            throw ServiceException.of(ArtResourceErrorCode.UNSAFE_PATH);
    }
    public static boolean digest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception ex) { throw new IllegalStateException("运行环境缺少SHA-256", ex); }
    }
    private static boolean positiveId(String text) {
        if (!text.matches("[1-9][0-9]{0,18}")) return false;
        try { return Long.parseLong(text) > 0; } catch (NumberFormatException ex) { return false; }
    }
    private static String text(JsonNode node, String key, int max) {
        JsonNode value = node.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > max
                || value.asText().chars().anyMatch(c -> c < 32 || c == 127)) fail();
        // 拒绝JSON转义形成的孤立代理码元，避免跨语言UTF-8编码出现不同替换字符。
        String result = value.asText();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i))) fail();
            } else if (Character.isLowSurrogate(c)) fail();
        }
        return result;
    }
    private static void fields(JsonNode node, Set<String> required, Set<String> optional) {
        if (node == null || !node.isObject() || node.size() < required.size()) fail();
        for (String key : required) if (!node.has(key)) fail();
        node.fieldNames().forEachRemaining(key -> { if (!required.contains(key) && !optional.contains(key)) fail(); });
    }
    private static void fail() { throw ServiceException.of(ArtResourceErrorCode.INVALID_CLOSURE); }
}
