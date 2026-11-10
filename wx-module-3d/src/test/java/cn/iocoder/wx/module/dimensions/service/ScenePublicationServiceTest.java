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
        String auditSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/003-publication-audit.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : auditSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        String referenceSchema = Files.readString(Path.of("../deploy/test148/migrations/data09/004-publication-resource.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").replace(" COLLATE utf8mb4_bin", "");
        for (String sql : referenceSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_scope VARCHAR(32),tenant_id VARCHAR(20),resource_type VARCHAR(20),current_revision BIGINT,del_flag CHAR,content_state VARCHAR(16))");
        jdbc.execute("CREATE TABLE wx_3d_resource_operation(id VARCHAR(36) PRIMARY KEY,resource_id BIGINT,committed_revision BIGINT,resource_scope VARCHAR(32),tenant_id VARCHAR(20),status VARCHAR(16),after_snapshot LONGTEXT)");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        var target = new ScenePublicationService();
        ReflectionTestUtils.setField(target, "jdbc", jdbc);
        ReflectionTestUtils.setField(target, "access", access);
        var dependencies = new ScenePublicationDependencyService();
        ReflectionTestUtils.setField(dependencies, "jdbc", jdbc);
        ReflectionTestUtils.setField(dependencies, "parser", new ArtResourceManifestVerifier());
        ReflectionTestUtils.setField(target, "dependencies", dependencies);
        var factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(data), new AnnotationTransactionAttributeSource()));
        service = (ScenePublicationService) factory.getProxy();
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id,deleted) VALUES(?,?,FALSE)", 101L, 2L);
        insertRevision(1, 101, 1);
    }

    private void insertRevision(long id, long sceneId, int revisionNo) {
        jdbc.update("INSERT INTO wx_3d_scene_revision(id,tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json,content_hash) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, 2L, sceneId, revisionNo, 1, "{}", "{}", "{}", "{\"resources\":[]}",
                SceneRevisionContentHasher.sha256(1, "{}", "{}", "{}", "{\"resources\":[]}"));
    }

    @Test void publishIsIdempotentAndKeepsOnlyOneCurrentPointer() {
        var first = service.publish("101", 1, "pub-1");
        var replay = service.publish("101", 1, "pub-1");
        assertEquals(first.get("publicationId"), replay.get("publicationId"));

        insertRevision(2, 101, 2);
        var second = service.publish("101", 2, "pub-2");
        assertNotEquals(first.get("publicationId"), second.get("publicationId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication WHERE current_flag=1", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
        assertEquals(2, service.current("101").get("revisionNo"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_audit WHERE action='PUBLISH'", Integer.class));
    }

    @Test void rejectCrossTenantAndMismatchedReplay() {
        service.publish("101", 1, "pub-1");
        insertRevision(2, 101, 2);
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

    @Test void missingResourceDependencyCannotBecomeCurrentPublication() {
        jdbc.update("UPDATE wx_3d_scene_revision SET dependencies_json=?,content_hash=NULL WHERE scene_id=101",
                "{\"resources\":[{\"resourceKey\":\"model-1\",\"assetId\":\"999\",\"kind\":\"unity-model\"}]}");
        assertEquals(409, assertThrows(ServiceException.class, () -> service.publish("101", 1, "missing-resource")).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
    }

    @Test void nonEmptyStoredHashMustMatchFourPackagesBeforePublishing() {
        jdbc.update("UPDATE wx_3d_scene_revision SET content_hash=? WHERE scene_id=101", "f".repeat(64));
        assertEquals(409, assertThrows(ServiceException.class, () -> service.publish("101", 1, "wrong-source-hash")).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
    }

    @Test void snapshotReadReturnsPublishedDependencyWithoutFollowingDraft() {
        var published = service.publish("101", 1, "snapshot-1");
        jdbc.update("UPDATE wx_3d_scene_revision SET dependencies_json=? WHERE scene_id=? AND revision_no=?", "{\"resources\":[\"changed\"]}", 101L, 1);
        var snapshot = service.readSnapshot(published.get("publicationId").toString());
        assertEquals("{\"resources\":[]}", snapshot.get("dependencySnapshot"));
    }

    private void resource(long id, String owner) throws Exception {
        String scope = "0".equals(owner) ? "GLOBAL_SYSTEM" : "TENANT_SHARED";
        jdbc.update("INSERT INTO wx_3d_resource VALUES(?,?,?,'MODEL',1,'0','READY')", id, scope, owner);
        var snapshot = new ObjectMapper().createObjectNode();
        snapshot.putObject("resource").put("id", Long.toString(id)).put("current_revision", "1")
                .put("content_hash", "a".repeat(64)).put("manifest_sha256", "b".repeat(64))
                .put("content_state", "READY").put("del_flag", "0").put("content_format", "unity.assetbundle.v1")
                .put("platform", "WebGL").put("app_compatibility", "test").put("storage_prefix", "resources/frozen/").put("manifest_path", "manifest.json");
        jdbc.update("INSERT INTO wx_3d_resource_operation VALUES(?,?,1,?,?,'COMMITTED',?)", "op-" + id, id, scope, owner, snapshot.toString());
    }

    private void dependencies(String resources) {
        // 此处模拟保存后的修订原文，清空共享摘要以覆盖兼容期按原文计算的分支。
        jdbc.update("UPDATE wx_3d_scene_revision SET dependencies_json=?,content_hash=NULL WHERE scene_id=101", "{\"resources\":" + resources + "}");
    }

    @Test void freezesSharedResourceOncePerKeyAndReplayIgnoresCurrentChanges() throws Exception {
        resource(21, "2");
        dependencies("[{\"resourceKey\":\"one\",\"assetId\":\"21\",\"kind\":\"unity-model\"},{\"resourceKey\":\"two\",\"assetId\":\"21\",\"kind\":\"unity-model\"}]");
        var first = service.publish("101", 1, "with-resources");
        jdbc.update("UPDATE wx_3d_resource SET current_revision=2 WHERE id=21");
        assertEquals(first.get("publicationId"), service.publish("101", 1, "with-resources").get("publicationId"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_resource", Integer.class));
        assertEquals(1L, jdbc.queryForObject("SELECT MIN(resource_revision) FROM wx_3d_scene_publication_resource", Long.class));
        assertEquals(2, service.readSnapshot(first.get("publicationId").toString()).get("indexVersion"));
    }

    @Test void invalidDependenciesLeavePreviousPublicationUntouched() throws Exception {
        resource(21, "2"); resource(31, "3");
        var before = service.publish("101", 1, "before-invalid");
        for (String resource : new String[]{
                "{\"resourceKey\":\"x\",\"assetId\":\"31\",\"kind\":\"unity-model\"}",
                "{\"resourceKey\":\"x\",\"assetId\":\"21\",\"kind\":\"unity-model\",\"revision\":\"2\"}",
                "{\"resourceKey\":\"x\",\"assetId\":\"21\",\"kind\":\"unity-model\",\"contentHash\":\"" + "c".repeat(64) + "\"}",
                "{\"resourceKey\":\"x\",\"assetId\":\"21\",\"kind\":\"image\"}"}) {
            dependencies("[" + resource + "]");
            assertEquals(409, assertThrows(ServiceException.class, () -> service.publish("101", 1, "invalid-new")).getCode());
            assertEquals(before.get("publicationId"), service.current("101").get("publicationId"));
        }
        dependencies("[{\"resourceKey\":\"x\",\"assetId\":\"21\",\"kind\":\"unity-model\"},{\"resourceKey\":\"x\",\"assetId\":\"21\",\"kind\":\"unity-model\"}]");
        assertThrows(ServiceException.class, () -> service.publish("101", 1, "duplicate-key"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_resource", Integer.class));
    }

    @Test void rollbackReusesPinnedResourceRevisionAndKeepsHistoricalReferences() throws Exception {
        resource(11, "0");
        dependencies("[{\"resourceKey\":\"global\",\"assetId\":\"11\",\"kind\":\"unity-model\"}]");
        var first = service.publish("101", 1, "original-resource");
        jdbc.update("UPDATE wx_3d_resource SET current_revision=2 WHERE id=11");
        service.revoke(first.get("publicationId").toString(), "回退测试");
        var restored = service.rollback(first.get("publicationId").toString(), "restore-resource");
        assertNotEquals(first.get("publicationId"), restored.get("publicationId"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_resource WHERE resource_revision=1", Integer.class));
    }

    @Test void referenceWriteFailureRollsBackNewPointerAndAudit() throws Exception {
        resource(21, "2");
        var before = service.publish("101", 1, "before-write-failure");
        dependencies("[{\"resourceKey\":\"deny\",\"assetId\":\"21\",\"kind\":\"unity-model\"}]");
        jdbc.execute("ALTER TABLE wx_3d_scene_publication_resource ADD CONSTRAINT injected_failure CHECK (resource_key<>'deny')");
        assertThrows(RuntimeException.class, () -> service.publish("101", 1, "failed-write"));
        assertEquals(before.get("publicationId"), service.current("101").get("publicationId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_audit", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication_resource", Integer.class));
    }

    @Test void explicitHistoricalRevisionDoesNotFollowCurrentResource() throws Exception {
        resource(21, "2");
        jdbc.update("UPDATE wx_3d_resource SET current_revision=2 WHERE id=21");
        dependencies("[{\"resourceKey\":\"old\",\"assetId\":\"21\",\"kind\":\"unity-model\",\"revision\":\"1\",\"contentHash\":\"" + "a".repeat(64) + "\",\"manifestSha256\":\"" + "b".repeat(64) + "\"}]");
        service.publish("101", 1, "explicit-old");
        assertEquals(1L, jdbc.queryForObject("SELECT resource_revision FROM wx_3d_scene_publication_resource", Long.class));
    }

    @Test void resourceAvailabilityAndCommittedScopeAreRechecked() throws Exception {
        resource(21, "2");
        dependencies("[{\"resourceKey\":\"model\",\"assetId\":\"21\",\"kind\":\"unity-model\"}]");
        for (String mutation : new String[]{"UPDATE wx_3d_resource SET del_flag='1'", "UPDATE wx_3d_resource SET content_state='PENDING'",
                "UPDATE wx_3d_resource_operation SET status='VERIFYING'", "UPDATE wx_3d_resource_operation SET tenant_id='3'"}) {
            jdbc.update(mutation);
            assertThrows(ServiceException.class, () -> service.publish("101", 1, "unavailable"));
            jdbc.update("UPDATE wx_3d_resource SET del_flag='0',content_state='READY'");
            jdbc.update("UPDATE wx_3d_resource_operation SET status='COMMITTED',tenant_id='2'");
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_publication", Integer.class));
    }

    @Test void duplicateJsonAndOversizedDependencySetsFailClosed() {
        jdbc.update("UPDATE wx_3d_scene_revision SET content_hash=NULL,dependencies_json=?", "{\"resources\":[],\"resources\":[]}");
        assertThrows(ServiceException.class, () -> service.publish("101", 1, "duplicate-json"));
        var array = new ObjectMapper().createArrayNode();
        for (int i = 0; i < 257; i++) array.addObject().put("resourceKey", "key-" + i).put("assetId", "21").put("kind", "unity-model");
        dependencies(array.toString());
        assertThrows(ServiceException.class, () -> service.publish("101", 1, "too-many-resources"));
    }
}
