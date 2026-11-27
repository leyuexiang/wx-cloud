package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.config.GisCatalogProperties;
import cn.iocoder.wx.module.dimensions.domain.vo.GisCatalogEntry;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 只读取受控配置中的既有描述文件，不迁移对象，也不把存储故障伪装为空目录。 */
@Service
public class GisCatalogService {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final List<String> TYPES = List.of("ELEVATION", "SATELLITE");
    private final GisCatalogProperties properties;
    private final ObjectMapper json;
    private final Map<String, Cache> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = Map.of("ELEVATION", new Object(), "SATELLITE", new Object());
    private record Cache(long expires, List<GisCatalogEntry> entries) {}

    public GisCatalogService(GisCatalogProperties properties, ObjectMapper json) {
        this.properties = properties; this.json = json;
    }

    public List<GisCatalogEntry> list(String dataType) {
        // 非 HTTP 调用也遵守相同参数边界，避免空值落入集合查询产生空指针。
        if (dataType == null || !TYPES.contains(dataType)) throw new ServiceException(400, "dataType 仅支持 ELEVATION 或 SATELLITE");
        if (!properties.isEnabled()) throw unavailable();
        var cached = cache.get(dataType);
        if (cached != null && cached.expires > System.nanoTime()) return cached.entries;
        // 相同类别只保留一个刷新者；失败不提交部分结果，既有场景仍可继续使用原配置。
        synchronized (locks.get(dataType)) {
            cached = cache.get(dataType);
            if (cached != null && cached.expires > System.nanoTime()) return cached.entries;
            try {
                var sources = properties.getSources();
                if (sources == null || sources.isEmpty() || sources.size() > 16) throw unavailable();
                var keys = new HashSet<String>(); var identifiers = new HashSet<String>();
                for (var source : sources) {
                    if (source == null || source.getKey() == null || !source.getKey().matches("[a-z0-9][a-z0-9._-]{0,95}")
                            || !TYPES.contains(source.getDataType()) || source.getName() == null || source.getName().isBlank()
                            || !keys.add(source.getKey()) || !identifiers.add(identifier(source.getKey()))) throw unavailable();
                    address(source.getMetadataUrl());
                    if (!"https".equals(address(source.getPublicUrl()).getScheme())) throw unavailable();
                    if (source.getMaximumLevel() != null && (source.getMaximumLevel() < 0 || source.getMaximumLevel() > 30)) throw unavailable();
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
                var entries = new ArrayList<GisCatalogEntry>();
                for (var source : sources) if (dataType.equals(source.getDataType()))
                    entries.add(read(source, fetch(address(source.getMetadataUrl()), deadline)));
                var result = List.copyOf(entries);
                long ttl = Math.min(300, Math.max(0, properties.getCacheSeconds()));
                cache.put(dataType, new Cache(System.nanoTime() + TimeUnit.SECONDS.toNanos(ttl), result));
                return result;
            } catch (ServiceException error) { throw error; }
            catch (Exception error) { throw unavailable(); }
        }
    }

    private static ServiceException unavailable() { return new ServiceException(503, "GIS 目录来源暂不可用，请保留当前配置并稍后重试"); }

    private static URI address(String value) {
        if (value == null) throw unavailable();
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) throw unavailable();
        return uri;
    }

    private byte[] fetch(URI uri, long deadline) throws Exception {
        var connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            int remaining = remaining(deadline);
            connection.setConnectTimeout(Math.max(1, Math.min(remaining, properties.getConnectTimeoutMillis())));
            connection.setReadTimeout(Math.max(1, Math.min(remaining, properties.getReadTimeoutMillis())));
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            // 这是共享公开元数据读取，不转发业务请求的登录头、租户或 Cookie。
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_BYTES) throw unavailable();
            try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                while (true) {
                    connection.setReadTimeout(Math.max(1, Math.min(remaining(deadline), properties.getReadTimeoutMillis())));
                    int length = input.read(buffer); if (length < 0) break;
                    if (output.size() + length > MAX_BYTES) throw unavailable();
                    output.write(buffer, 0, length);
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

    private static int remaining(long deadline) {
        long milliseconds = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (milliseconds <= 0) throw unavailable();
        return (int) Math.min(milliseconds, Integer.MAX_VALUE);
    }

    private GisCatalogEntry read(GisCatalogProperties.Source source, byte[] bytes) throws Exception {
        String coordinate, tileSize = "", extension = "", rule = "tms";
        List<Double> bounds; int level;
        if ("SATELLITE".equals(source.getDataType())) {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder(); builder.setErrorHandler(new DefaultHandler());
            Element root = builder.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!"TileMap".equals(root.getTagName())) throw unavailable();
            coordinate = text(root, "SRS");
            Element format = child(root, "TileFormat");
            tileSize = format.getAttribute("width"); extension = format.getAttribute("extension").toLowerCase(java.util.Locale.ROOT);
            int pixels = Integer.parseInt(tileSize);
            if (pixels < 1 || pixels > 4096 || !List.of("jpg", "jpeg", "png").contains(extension)) throw unavailable();
            Element box = child(root, "BoundingBox");
            bounds = bounds(List.of(Double.parseDouble(box.getAttribute("minx")), Double.parseDouble(box.getAttribute("miny")),
                    Double.parseDouble(box.getAttribute("maxx")), Double.parseDouble(box.getAttribute("maxy"))));
            var sets = root.getElementsByTagName("TileSet"); level = -1;
            for (int i = 0; i < sets.getLength(); i++) level = Math.max(level, Integer.parseInt(((Element) sets.item(i)).getAttribute("order")));
        } else {
            JsonNode metadata = json.readTree(bytes);
            if (!"quantized-mesh-1.0".equals(metadata.path("format").asText()) || !metadata.path("tiles").isArray()
                    || metadata.path("tiles").isEmpty()) throw unavailable();
            coordinate = metadata.path("projection").asText(); rule = metadata.path("schema").asText("tms");
            level = metadata.path("available").isArray() ? metadata.path("available").size() - 1 : -1;
            var box = metadata.path("bounds"); var values = new ArrayList<Double>();
            if (box.isArray()) for (var value : box) values.add(value.isNumber() ? value.asDouble() : Double.NaN);
            bounds = bounds(values);
        }
        // 当前播放器的本地影像按地理坐标格网消费；不把其他投影伪装为可直接加载。
        if (!"EPSG:4326".equals(coordinate) || level < 0 || level > 30 || !"tms".equals(rule)) throw unavailable();
        if (source.getMaximumLevel() != null) level = Math.min(level, source.getMaximumLevel());
        boolean global = bounds.size() != 4 || (bounds.get(2) - bounds.get(0) >= 350 && bounds.get(3) - bounds.get(1) >= 170);
        String latitude = global ? "" : number((bounds.get(1) + bounds.get(3)) / 2);
        String longitude = global ? "" : number((bounds.get(0) + bounds.get(2)) / 2);
        String metadataPath = address(source.getMetadataUrl()).getPath();
        return new GisCatalogEntry(identifier(source.getKey()), source.getName(), source.getDataType(), coordinate,
                level, tileSize, source.getPublicUrl(), metadataPath.substring(metadataPath.lastIndexOf('/') + 1),
                false, rule, latitude, longitude, bounds, extension);
    }

    private static Element child(Element parent, String tag) {
        var nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() != 1) throw unavailable();
        return (Element) nodes.item(0);
    }
    private static String text(Element parent, String tag) { return child(parent, tag).getTextContent().trim(); }
    private static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private static List<Double> bounds(List<Double> values) {
        if (values.size() != 4 || values.stream().anyMatch(value -> !Double.isFinite(value)) || values.get(0) < -180
                || values.get(1) < -90 || values.get(2) > 180 || values.get(3) > 90
                || values.get(0) >= values.get(2) || values.get(1) >= values.get(3)) return List.of();
        return List.copyOf(values);
    }
    private static String identifier(String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(("gis-catalog:v1:" + key).getBytes(StandardCharsets.UTF_8));
        byte[] truncated = java.util.Arrays.copyOf(digest, 8); truncated[0] &= 0x7f;
        return new BigInteger(1, truncated).max(BigInteger.ONE).toString();
    }
}
