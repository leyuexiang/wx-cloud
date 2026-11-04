package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实SQL与事务代理检查并发保存、租约和身份边界，不依赖服务器测试账号。 */
class Dim3dSceneServiceTest {
    private Dim3dSceneService service;
    private ArtResourceAccess access;
    private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        String schema = Files.readString(Path.of("../deploy/test148/migrations/scene01/001-scene.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : schema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        var target = new Dim3dSceneService(); access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        ReflectionTestUtils.setField(target, "jdbc", jdbc); ReflectionTestUtils.setField(target, "access", access); ReflectionTestUtils.setField(target, "json", json);
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (Dim3dSceneService) factory.getProxy();
    }

    private String create() {
        return (String) service.create(json.createObjectNode().put("name", "测试场景").put("sceneType", "local3d")).get("sceneId");
    }
    @SuppressWarnings("unchecked") private ObjectNode saveBody(String id, String token) {
        var revision = (Map<String, Object>) service.info(id).get("currentRevision");
        var body = json.createObjectNode().put("sceneId", id).put("lockToken", token).put("requestId", UUID.randomUUID().toString())
                .put("baseRevisionNo", ((Number) revision.get("revisionNo")).intValue()).put("schemaVersion", 1);
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) body.put(key, (String) revision.get(key));
        return body;
    }

    @Test void creationIncludesCompleteFirstRevisionAndTenant() {
        String id = create(); var detail = service.info(id);
        assertEquals("2", detail.get("tenantId")); assertTrue(Long.parseLong(id) > Integer.MAX_VALUE);
        assertEquals(1, saveBody(id, "unused").get("baseRevisionNo").asInt());
        assertEquals(1L, service.page(null, null, null, null, null, null, 1, 10).get("total"));
    }
    @Test void otherTenantCannotReadOrLockEvenWithKnownId() {
        String id = create(); when(access.actor()).thenReturn(new ArtResourceAccess.Actor(5, 3));
        assertThrows(ServiceException.class, () -> service.info(id));
        assertThrows(ServiceException.class, () -> service.lock(id, "foreign"));
        assertEquals(0L, service.page(null, null, null, null, null, null, 1, 10).get("total"));
    }
    @Test void sameClientReplaysLeaseAndOtherClientCannotUnlockIt() {
        String id = create(); var lease = service.lock(id, "one");
        assertEquals(lease.get("lockToken"), service.lock(id, "one").get("lockToken"));
        assertThrows(ServiceException.class, () -> service.lock(id, "two"));
        service.unlock(id, "two"); assertThrows(ServiceException.class, () -> service.lock(id, "two"));
        service.unlock(id, "one"); assertNotEquals(lease.get("lockToken"), service.lock(id, "two").get("lockToken"));
    }
    @Test void saveIsAtomicAndSameRequestDoesNotIncrementTwice() {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        ObjectNode body = saveBody(id, token); var first = service.save(body);
        assertEquals(2, first.get("revisionNo")); assertEquals(first.get("revisionId"), service.save(body).get("revisionId"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_scene_revision", Integer.class));
        body.put("twoDJson", "{\"changed\":true}"); assertThrows(ServiceException.class, () -> service.save(body));
    }
    @Test void staleRevisionIsConflictAndBrokenPackageDoesNotWrite() {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        ObjectNode first = saveBody(id, token), stale = saveBody(id, token);
        service.save(first); assertEquals(true, service.save(stale).get("conflict"));
        var broken = saveBody(id, token).put("threeDJson", "[]");
        assertThrows(ServiceException.class, () -> service.save(broken));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_scene_revision", Integer.class));
    }
    @Test void expiredLockCannotSaveAndDeletedSceneCannotReopen() {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        var body = saveBody(id, token);
        jdbc.update("UPDATE wx_dim3d_scene SET lock_expire='2000-01-01 00:00:00' WHERE id=?", Long.parseLong(id));
        assertThrows(ServiceException.class, () -> service.save(body)); service.remove(id);
        assertThrows(ServiceException.class, () -> service.info(id));
    }
    @Test void concurrentSavesWithOneBaseCommitOnlyOneRevision() throws Exception {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        var one = saveBody(id, token); var two = saveBody(id, token);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> service.save(one)); var b = pool.submit(() -> service.save(two));
            assertNotEquals(a.get().get("conflict"), b.get().get("conflict"));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_scene_revision", Integer.class));
        } finally { pool.shutdownNow(); }
    }
}
