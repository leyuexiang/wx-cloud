package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.config.GisCatalogProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实本机 HTTP 验证目录读取、缓存及失败边界，不以替身代替解析实现。 */
class GisCatalogServiceTest {
    private HttpServer server;
    private String base;
    private final AtomicInteger reads = new AtomicInteger();
    private GisCatalogProperties properties;
    private static final String XML = "<TileMap><Title>实际影像</Title><SRS>EPSG:4326</SRS>"
            + "<BoundingBox minx=\"110\" miny=\"19\" maxx=\"112\" maxy=\"21\"/>"
            + "<Origin x=\"-180\" y=\"-90\"/><TileFormat extension=\"jpg\" width=\"256\" height=\"256\"/>"
            + "<TileSets profile=\"global-geodetic\"><TileSet order=\"0\"/><TileSet order=\"20\"/></TileSets></TileMap>";

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        properties = new GisCatalogProperties(); properties.setEnabled(true);
        server.start();
    }
    @AfterEach void close() { server.stop(0); }
    private void serve(String path, String body) {
        server.createContext(path, exchange -> {
            reads.incrementAndGet(); byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
    }
    private GisCatalogProperties.Source source(String key, String type, String path) {
        var source = new GisCatalogProperties.Source();
        source.setKey(key); source.setName("已登记的来源"); source.setDataType(type);
        source.setMetadataUrl(base + path); source.setPublicUrl("https://map.test/gis-source/" + key);
        source.setMaximumLevel(8); return source;
    }
    private GisCatalogService service() { return new GisCatalogService(properties, new ObjectMapper()); }

    @Test void readsRealImageryMetadataAndCapsUnavailableLevels() {
        serve("/tms.xml", XML);
        properties.setSources(List.of(source("world", "SATELLITE", "/tms.xml")));
        var item = service().list("SATELLITE").get(0);
        assertEquals("EPSG:4326", item.coordinate()); assertEquals("256", item.tileSize());
        assertEquals(8, item.level()); assertEquals("20", item.latitude()); assertEquals("111", item.longitude());
        assertEquals("tms", item.sliceRule()); assertFalse(item.sliceFlag());
        assertTrue(item.id().matches("[1-9][0-9]{0,18}"));
        assertEquals("https://map.test/gis-source/world", item.path());
        assertEquals(List.of(110d, 19d, 112d, 21d), item.bounds()); assertEquals("jpg", item.fileExtension());
    }

    @Test void readsQuantizedTerrainWithoutInventingPixelSizeOrInvalidCenter() {
        serve("/layer.json", "{\"format\":\"quantized-mesh-1.0\",\"projection\":\"EPSG:4326\","
                + "\"schema\":\"tms\",\"tiles\":[\"{z}/{x}/{y}.terrain\"],\"available\":[[],[],[]],"
                + "\"bounds\":[-180,-90,360,270]}");
        properties.setSources(List.of(source("terrain", "ELEVATION", "/layer.json")));
        var item = service().list("ELEVATION").get(0);
        assertEquals(2, item.level()); assertEquals("", item.tileSize());
        assertEquals("", item.latitude()); assertEquals("", item.longitude());
    }

    @Test void cachesImmutableResultsAndKeepsIdStableAcrossAddressChanges() {
        serve("/a.xml", XML); serve("/b.xml", XML);
        var source = source("world", "SATELLITE", "/a.xml"); properties.setSources(List.of(source));
        var service = service(); var first = service.list("SATELLITE");
        assertEquals(first, service.list("SATELLITE")); assertEquals(1, reads.get());
        assertThrows(UnsupportedOperationException.class, () -> first.clear());
        source.setMetadataUrl(base + "/b.xml"); source.setName("新显示名");
        assertEquals(first.get(0).id(), service().list("SATELLITE").get(0).id());
    }

    @Test void rejectsInvalidTypeDisabledCatalogAndDuplicateSourceIdentity() {
        assertThrows(ServiceException.class, () -> service().list(null));
        assertThrows(ServiceException.class, () -> service().list("other"));
        properties.setEnabled(false); assertThrows(ServiceException.class, () -> service().list("SATELLITE"));
        properties.setEnabled(true);
        properties.setSources(List.of(source("same", "SATELLITE", "/a"), source("same", "SATELLITE", "/b")));
        assertThrows(ServiceException.class, () -> service().list("SATELLITE"));
    }

    @Test void rejectsExternalEntitiesWithoutFetchingTheirTarget() {
        serve("/private", "不可读取");
        serve("/xxe.xml", "<!DOCTYPE TileMap [<!ENTITY secret SYSTEM \"" + base + "/private\">]>"
                + XML.replace("实际影像", "&secret;"));
        properties.setSources(List.of(source("world", "SATELLITE", "/xxe.xml")));
        assertThrows(ServiceException.class, () -> service().list("SATELLITE")); assertEquals(1, reads.get());
    }

    @Test void rejectsRedirectsAndOversizedResponses() {
        serve("/real", XML);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/real");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        properties.setSources(List.of(source("world", "SATELLITE", "/redirect")));
        assertThrows(ServiceException.class, () -> service().list("SATELLITE")); assertEquals(0, reads.get());
        serve("/huge", "x".repeat(1024 * 1024 + 1));
        properties.setSources(List.of(source("world", "SATELLITE", "/huge")));
        assertThrows(ServiceException.class, () -> service().list("SATELLITE"));
    }
}
