package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 验证发布幂等、当前指针、撤销、回退和租户边界。 */
class ScenePublicationServiceTest {
    private ScenePublicationService service;
    private JdbcTemplate jdbc;
    private ArtResourceAccess access;

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE wx_3d_scene(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,deleted BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE wx_3d_scene_revision(id BIGINT PRIMARY KEY,tenant_id BIGINT NOT NULL,scene_id BIGINT NOT NULL,revision_no INT NOT NULL,schema_version INT NOT NULL,scene_json LONGTEXT NOT NULL,three_d_json LONGTEXT NOT NULL,two_d_json LONGTEXT NOT NULL,dependencies_json LONGTEXT NOT NULL,content_hash CHAR(64))");
        String schema = Files.readString(Path.of("../deploy/test148/migrations/data09/001-scene-publication.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : schema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        var target = new ScenePublicationService();
        ReflectionTestUtils.setField(target, "jdbc", jdbc);
        ReflectionTestUtils.setField(target, "access", access);
        ReflectionTestUtils.setField(target, "json", new ObjectMapper());
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (ScenePublicationService) factory.getProxy();
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id,deleted) VALUES(?,?,FALSE)", 101L, 2L);
        insertRevision(1, 101, 1, "a".repeat(64));
    }

    private void insertRevision(long id, long sceneId, int revisionNo, String hash) {
        jdbc.update("INSERT INTO wx_3d_scene_revision(id,tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json,content_hash) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, 2L, sceneId, revisionNo, 1, "{}", "{}", "{}", "{\"resources\":[]}", hash);
    }

    @Test void publishIsIdempotentAndKeepsOnlyOneCurrentPointer() {
        var first = service.publish("101", 1, "pub-1");
        var replay = service.publish("101", 1, "pub-1");
        assertEquals(first.get("publicationId"), replay.get("publicationId"));

        insertRevision(2, 101, 2, "b".repeat(64));
        var second = service.publish("101", 2, "pub-2");
        assertNotEquals(first.get("publicationId"), second.get("publicationId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication WHERE current_flag=1", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
        assertEquals(2, service.current("101").get("revisionNo"));
    }

    @Test void rejectCrossTenantAndMismatchedReplay() {
        service.publish("101", 1, "pub-1");
        insertRevision(2, 101, 2, "b".repeat(64));
        assertThrows(ServiceException.class, () -> service.publish("101", 2, "pub-1"));
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(9, 3));
        assertThrows(ServiceException.class, () -> service.current("101"));
        assertThrows(ServiceException.class, () -> service.publish("101", 1, "tenant-3"));
    }

    @Test void revokeAndRollbackPreserveImmutableHistory() {
        var first = service.publish("101", 1, "pub-1");
        service.revoke(first.get("publicationId").toString(), "撤销测试");
        assertThrows(ServiceException.class, () -> service.current("101"));
        var rollback = service.rollback(first.get("publicationId").toString(), "pub-rollback");
        assertNotEquals(first.get("publicationId"), rollback.get("publicationId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication WHERE current_flag=1 AND status='PUBLISHED'", Integer.class));
        assertEquals("REVOKED", jdbc.queryForObject("SELECT status FROM wx_3d_scene_publication WHERE publication_id=?", String.class, first.get("publicationId")));
    }

    @Test void invalidDependencySnapshotCannotBePublished() {
        jdbc.update("UPDATE wx_3d_scene_revision SET dependencies_json=? WHERE scene_id=? AND revision_no=?", "[]", 101L, 1);
        assertThrows(ServiceException.class, () -> service.publish("101", 1, "invalid-dependencies"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
    }
}
