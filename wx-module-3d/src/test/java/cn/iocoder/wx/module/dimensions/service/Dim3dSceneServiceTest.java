package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实SQL与事务代理检查并发保存、租约和身份边界，不依赖服务器测试账号。 */
class Dim3dSceneServiceTest {
    private Dim3dSceneService service;
    private ArtResourceAccess access;
    private DictDataApi dictionaries;
    private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(data);
        // 历史迁移保留原名，测试数据库直接构建改名后的表结构。
        String schema = Files.readString(Path.of("../deploy/test148/migrations/scene01/001-scene.up.sql"))
                .replace("wx_dim3d_", "wx_3d_")
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : schema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        String templateSchema = Files.readString(Path.of("../deploy/test148/migrations/tpl01/001-scene-template.up.sql"))
                .replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : templateSchema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        jdbc.execute("CREATE TABLE wx_3d_template(id BIGINT PRIMARY KEY,template_name VARCHAR(50) NOT NULL,template_desc VARCHAR(500),template_type VARCHAR(16) NOT NULL,category VARCHAR(32) NOT NULL,resource_belong VARCHAR(16) NOT NULL,cover_image_id BIGINT,template_file_id BIGINT NOT NULL,status VARCHAR(16) NOT NULL,tenant_id VARCHAR(20) NOT NULL,del_flag CHAR(1) NOT NULL,update_time TIMESTAMP)");
        var target = new Dim3dSceneService(); access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(2, 2));
        dictionaries = mock(DictDataApi.class);
        when(dictionaries.validateDictDataList(anyString(), eq(List.of("测试")))).thenReturn(CommonResult.success(true));
        ReflectionTestUtils.setField(target, "dictionaries", dictionaries);
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

    @Test void invalidNodeTreesCannotCreateRevision() throws Exception {
        String id = create(); String token = (String) service.lock(id, "nodes").get("lockToken");
        // 直接调用真实保存事务，证明非法树在写修订前被拒绝。
        for (String nodes : java.util.List.of(
                "[{\"instanceId\":\"a\"},{\"instanceId\":\"a\"}]",
                "[{\"instanceId\":\"a\",\"parentInstanceId\":\"missing\"}]",
                "[{\"instanceId\":\"a\",\"parentInstanceId\":\"b\"},{\"instanceId\":\"b\",\"parentInstanceId\":\"a\"}]")) {
            ObjectNode body = saveBody(id, token);
            ObjectNode threeD = (ObjectNode) json.readTree(body.path("threeDJson").asText());
            threeD.set("objects", json.readTree(nodes)); body.put("threeDJson", threeD.toString());
            assertThrows(ServiceException.class, () -> service.save(body));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision", Integer.class));
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
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision", Integer.class));
        body.put("twoDJson", "{\"changed\":true}"); assertThrows(ServiceException.class, () -> service.save(body));
    }
    @Test void staleRevisionIsConflictAndBrokenPackageDoesNotWrite() {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        ObjectNode first = saveBody(id, token), stale = saveBody(id, token);
        service.save(first); assertEquals(true, service.save(stale).get("conflict"));
        var broken = saveBody(id, token).put("threeDJson", "[]");
        assertThrows(ServiceException.class, () -> service.save(broken));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision", Integer.class));
    }
    @Test void expiredLockCannotSaveAndDeletedSceneCannotReopen() {
        String id = create(); String token = (String) service.lock(id, "one").get("lockToken");
        var body = saveBody(id, token);
        jdbc.update("UPDATE wx_3d_scene SET lock_expire='2000-01-01 00:00:00' WHERE id=?", Long.parseLong(id));
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
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void snapshotTemplateCreatesIndependentSceneAndIsIdempotent() {
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode body = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-request-1").put("templateName", "测试模板").put("templateType", "INDUSTRY")
                .put("category", "测试").put("saveScope", "all").put("templateDesc", "快照模板");
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) body.put(key, (String) revision.get(key));
        var first = service.saveAsTemplateFromSnapshot(body);
        assertEquals("all", first.get("saveScope"));
        assertEquals(first.get("templateId"), service.saveAsTemplateFromSnapshot(body).get("templateId"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_template", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision WHERE scene_id=?", Integer.class, Long.parseLong(first.get("templateSceneId").toString())));
        assertEquals("测试模板", service.info(first.get("templateSceneId").toString()).get("name"));
        assertEquals("TEMPLATE", first.get("sceneKind"));
        assertEquals(first, service.saveAsTemplateFromSnapshot(body));
        String templateScene = first.get("sceneId").toString();
        assertEquals("TEMPLATE", service.info(templateScene).get("sceneKind"));
        assertEquals(1L, service.page(null, null, null, null, null, null, 1, 10).get("total"));
        assertThrows(ServiceException.class, () -> service.lock(templateScene, "template-edit"));
        assertThrows(ServiceException.class, () -> service.remove(templateScene));
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body.deepCopy().put("templateDesc", "不同描述")));
        assertEquals(1, ((Map<?, ?>) service.info(sourceId).get("currentRevision")).get("revisionNo"));
    }

    @Test void twoDSnapshotClearsThreeDAndRejectsStaleSource() throws Exception {
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode body = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-request-2").put("templateName", "二维模板").put("templateType", "EXHIBITION")
                .put("category", "测试").put("saveScope", "twoD");
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) body.put(key, (String) revision.get(key));
        // 使用网页实际发出的空三维包，测试被排除的节点无法随二维快照落库。
        body.put("threeDJson", """
                {"baseConfig":{},"objects":[],"components":[],"layers":[],"cameraPaths":[],
                 "streaming":{"groups":[],"chunks":[],"chunkTargets":[]}}
                """);
        var invalid = (ObjectNode) json.readTree(body.path("threeDJson").asText());
        invalid.putArray("objects").addObject().put("instanceId", "unexpected-node");
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body.deepCopy().put("threeDJson", invalid.toString())));
        var result = service.saveAsTemplateFromSnapshot(body);
        @SuppressWarnings("unchecked") var saved = (Map<String, Object>) service.info(result.get("templateSceneId").toString()).get("currentRevision");
        assertEquals(0, json.readTree((String) saved.get("threeDJson")).path("objects").size());
        body.put("sourceRevisionNo", "2").put("requestId", "tpl-request-3");
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body));
    }

    @Test void threeDSnapshotRejectsWrongCategoryCoverAndExcludedDependency() {
        String sourceId = create();
        ObjectNode body = saveBody(sourceId, "unused");
        body.put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("name", "三维模板").put("templateType", "dim3d_template_industry")
                .put("category", "测试").put("saveScope", "threeD");
        when(dictionaries.validateDictDataList("dim3d_template_industry", List.of("其他类别")))
                .thenReturn(CommonResult.success(false));
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body.deepCopy().put("category", "其他类别")));
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body.deepCopy().put("coverAssetId", "123")));
        var invalid = body.deepCopy().put("dependenciesJson", """
                {"resources":[],"behaviors":[],"deviceBindings":[],
                 "dataBindings":[{"owner":{"domain":"twoD","ownerType":"instance","instanceId":"excluded"}}]}
                """);
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(invalid));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_template", Integer.class));
        assertEquals("threeD", service.saveAsTemplateFromSnapshot(body).get("saveScope"));
        verify(access, atLeastOnce()).maintain(new ArtResourceAccess.Actor(2, 2), "GLOBAL_SYSTEM", "0");
    }

    @Test void deniedOperatorAndDirectoryFailureLeaveSourceUnchanged() {
        String sourceId = create();
        ObjectNode body = saveBody(sourceId, "unused")
                .put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("name", "故障模板").put("templateType", "INDUSTRY")
                .put("category", "测试").put("saveScope", "all");
        var actor = new ArtResourceAccess.Actor(2, 2);
        doThrow(new ServiceException(403, "无运营权限")).doNothing().when(access).maintain(actor, "GLOBAL_SYSTEM", "0");
        assertThrows(ServiceException.class, () -> service.saveAsTemplateFromSnapshot(body));
        verifyNoInteractions(dictionaries);
        // 让最后一次写入失败，确认事务会撤销此前插入的模板场景和首版修订。
        jdbc.execute("ALTER TABLE wx_3d_scene_template ADD CONSTRAINT reject_template_fixture CHECK (template_name <> '故障模板')");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> service.saveAsTemplateFromSnapshot(body));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_template", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wx_3d_scene_revision", Integer.class));
        var sourceRevision = (Map<?, ?>) service.info(sourceId).get("currentRevision");
        assertEquals(1, sourceRevision.get("revisionNo"));
        assertEquals(body.get("sceneJson").asText(), sourceRevision.get("sceneJson"));
    }

    @Test void snapshotTemplateDirectoryReadsDetailsAndSoftDeletes() {
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode body = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-request-3").put("templateName", "目录模板").put("templateType", "INDUSTRY")
                .put("category", "测试").put("saveScope", "all");
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) body.put(key, (String) revision.get(key));
        var created = service.saveAsTemplateFromSnapshot(body);
        assertEquals(1, service.templatePage(null, null, null, null, 1, 10).getTotal());
        assertEquals("目录模板", service.templateInfo(created.get("templateId").toString()).get("templateName"));
        service.changeTemplateStatus(created.get("templateId").toString(), "OFFLINE");
        assertEquals("OFFLINE", service.templateInfo(created.get("templateId").toString()).get("status"));
        service.removeTemplate(created.get("templateId").toString());
        assertEquals(0, service.templatePage(null, null, null, null, 1, 10).getTotal());
        assertEquals(sourceId, service.info(sourceId).get("sceneId"));
    }

    @Test void templateInstantiationCreatesIndependentSceneAndRemapsNodeReferences() throws Exception {
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode threeD = (ObjectNode) json.readTree((String) revision.get("threeDJson"));
        ArrayNode objects = threeD.putArray("objects");
        objects.addObject().put("instanceId", "source-parent").put("sortNo", 0);
        objects.addObject().put("instanceId", "source-child").put("parentInstanceId", "source-parent").put("layerKey", "source-layer").put("sortNo", 1);
        threeD.putArray("layers").addObject().put("layerKey", "source-layer");
        threeD.with("streaming").putArray("chunks").addObject().put("sceneId", sourceId).putArray("objectIds").add("source-child");
        ObjectNode templateBody = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-instance-source").put("templateName", "实例化模板").put("templateType", "INDUSTRY")
                .put("category", "测试").put("saveScope", "all");
        templateBody.put("threeDJson", threeD.toString());
        for (String key : java.util.List.of("sceneJson", "twoDJson", "dependenciesJson")) templateBody.put(key, (String) revision.get(key));
        // 使用正式四包字段，避免只检查父链而遗漏分块、二维和行为的悬空引用。
        ObjectNode twoD = (ObjectNode) json.readTree(templateBody.path("twoDJson").asText());
        ObjectNode widget = twoD.putArray("components").addObject().put("instanceId", "source-widget");
        widget.putObject("contentJson").putObject("widget").put("id", "source-widget");
        twoD.putArray("layerTree").addObject().put("id", "source-widget").put("type", "widget");
        templateBody.put("twoDJson", twoD.toString());
        templateBody.put("dependenciesJson", """
                {"resources":[{"id":"source-child","assetId":"source-child","owners":[{"domain":"threeD","ownerType":"layer","ownerKey":"source-layer"}]}],
                 "dataBindings":[{"owner":{"domain":"twoD","ownerType":"instance","instanceId":"source-widget"}}],
                 "behaviors":[{"owner":{"domain":"threeD","ownerType":"instance","instanceId":"source-child"},
                   "config":{"actionTargetUuid":"source-child","actionTargetWidgetIds":["source-widget"]}}],"deviceBindings":[]}
                """);
        ObjectNode scenePackage = (ObjectNode) json.readTree(templateBody.path("sceneJson").asText());
        scenePackage.putArray("sceneActions").addObject().putObject("config").put("actionTargetUuid", "source-child");
        templateBody.put("sceneJson", scenePackage.toString());
        var template = service.saveAsTemplateFromSnapshot(templateBody);
        ObjectNode instanceBody = json.createObjectNode().put("templateSceneId", template.get("templateSceneId").toString())
                .put("requestId", "tpl-instance-request").put("name", "实例场景");
        var instance = service.createFromTemplate(instanceBody);
        assertEquals("INSTANCE", instance.get("sceneKind"));
        assertEquals("实例场景", service.info(instance.get("sceneId").toString()).get("name"));
        @SuppressWarnings("unchecked") var instanceRevision = (Map<String, Object>) service.info(instance.get("sceneId").toString()).get("currentRevision");
        JsonNode savedThreeD = json.readTree((String) instanceRevision.get("threeDJson"));
        String parent = savedThreeD.path("objects").get(0).path("instanceId").asText();
        String child = savedThreeD.path("objects").get(1).path("instanceId").asText();
        assertNotEquals("source-parent", parent);
        assertNotEquals("source-child", child);
        assertEquals(parent, savedThreeD.path("objects").get(1).path("parentInstanceId").asText());
        assertEquals(child, savedThreeD.path("streaming").path("chunks").get(0).path("objectIds").get(0).asText());
        String layer = savedThreeD.path("layers").get(0).path("layerKey").asText();
        assertNotEquals("source-layer", layer);
        assertEquals(layer, savedThreeD.path("objects").get(1).path("layerKey").asText());
        JsonNode savedTwoD = json.readTree(instanceRevision.get("twoDJson").toString());
        String widgetId = savedTwoD.path("components").get(0).path("instanceId").asText();
        assertNotEquals("source-widget", widgetId);
        assertEquals(widgetId, savedTwoD.path("components").get(0).path("contentJson").path("widget").path("id").asText());
        assertEquals(widgetId, savedTwoD.path("layerTree").get(0).path("id").asText());
        JsonNode savedDependencies = json.readTree(instanceRevision.get("dependenciesJson").toString());
        assertEquals("source-child", savedDependencies.path("resources").get(0).path("id").asText());
        assertEquals("source-child", savedDependencies.path("resources").get(0).path("assetId").asText());
        assertEquals(layer, savedDependencies.path("resources").get(0).path("owners").get(0).path("ownerKey").asText());
        assertEquals(widgetId, savedDependencies.path("dataBindings").get(0).path("owner").path("instanceId").asText());
        assertEquals(child, savedDependencies.path("behaviors").get(0).path("config").path("actionTargetUuid").asText());
        assertEquals(widgetId, savedDependencies.path("behaviors").get(0).path("config").path("actionTargetWidgetIds").get(0).asText());
        assertEquals(child, json.readTree(instanceRevision.get("sceneJson").toString()).path("sceneActions").get(0).path("config").path("actionTargetUuid").asText());
        assertEquals(instance.get("sceneId").toString(), savedThreeD.path("streaming").path("chunks").get(0).path("sceneId").asText());
        assertEquals(instance.get("sceneId"), service.createFromTemplate(instanceBody).get("sceneId"));
        assertEquals(2L, service.page(null, null, null, null, null, null, 1, 10).get("total"));
        assertEquals("TEMPLATE", service.info(template.get("templateSceneId").toString()).get("sceneKind"));
    }

    @Test void templateInstantiationRequiresOnlineTemplateAndSameTenant() {
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode templateBody = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-instance-guard-source").put("templateName", "状态模板").put("templateType", "INDUSTRY")
                .put("category", "测试").put("saveScope", "all");
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) templateBody.put(key, (String) revision.get(key));
        var template = service.saveAsTemplateFromSnapshot(templateBody);
        ObjectNode instanceBody = json.createObjectNode().put("templateSceneId", template.get("templateSceneId").toString())
                .put("requestId", "tpl-instance-guard").put("name", "受保护实例");
        service.changeTemplateStatus(template.get("templateId").toString(), "OFFLINE");
        assertThrows(ServiceException.class, () -> service.createFromTemplate(instanceBody));
        service.changeTemplateStatus(template.get("templateId").toString(), "ONLINE");
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(9, 9));
        assertThrows(ServiceException.class, () -> service.createFromTemplate(instanceBody));
    }

    @Test void combinedTemplatePageKeepsLegacyAndSnapshotRecords() {
        jdbc.update("INSERT INTO wx_3d_template(id,template_name,template_type,category,resource_belong,template_file_id,status,tenant_id,del_flag,update_time) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                9001L, "旧文件模板", "INDUSTRY", "测试", "MINE", 7001L, "ONLINE", "2", "0");
        jdbc.update("INSERT INTO wx_3d_template(id,template_name,template_type,category,resource_belong,template_file_id,status,tenant_id,del_flag,update_time) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                9002L, "其他租户模板", "INDUSTRY", "测试", "SYSTEM", 7002L, "ONLINE", "3", "0");
        String sourceId = create();
        @SuppressWarnings("unchecked") var revision = (Map<String, Object>) service.info(sourceId).get("currentRevision");
        ObjectNode body = json.createObjectNode().put("sourceSceneId", sourceId).put("sourceRevisionNo", 1)
                .put("requestId", "tpl-request-combined").put("name", "快照模板").put("templateType", "dim3d_template_industry")
                .put("category", "测试").put("saveScope", "all");
        for (String key : java.util.List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson")) body.put(key, (String) revision.get(key));
        service.saveAsTemplateFromSnapshot(body);
        var page = service.templatePageCombined(null, "dim3d_template_industry", null, null, null, 1, 10);
        assertEquals(2, page.getTotal());
        assertEquals(java.util.Set.of("LEGACY", "SNAPSHOT"), page.getList().stream().map(row -> row.get("recordKind")).collect(java.util.stream.Collectors.toSet()));
    }
}
