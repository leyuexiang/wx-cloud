package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** 场景四包整体版本化。所有SQL显式限定认证租户，行锁协调编辑租约、保存和删除。 */
@Service
public class Dim3dSceneService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;
    @Resource private ObjectMapper json;
    @Resource private DictDataApi dictionaries;
    private static final String SUMMARY = "id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,current_revision,update_time";
    private static final List<String> PACKAGE_KEYS = List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson");
    // 模板首版复用场景修订表，目录关联用于阻止普通编辑入口改写模板。
    private static final String TEMPLATE_SCENE = """
            EXISTS (SELECT 1 FROM wx_3d_scene_template t
                    WHERE t.tenant_id=wx_3d_scene.tenant_id AND t.template_scene_id=wx_3d_scene.id)
            """;

    public Map<String, Object> page(String name, String type, String category, String kind, Integer published, String tenant, int number, int size) {
        var actor = access.actor();
        if (number < 1 || number > 10000 || size < 1 || size > 100) throw bad("分页范围无效");
        // 模板和发布功能尚未接入，不将编辑草稿冒充模板或已发布内容。
        if ((kind != null && !"INSTANCE".equals(kind)) || (published != null && published != 0) ||
                (tenant != null && !tenant.isBlank() && !Long.toString(actor.tenant()).equals(tenant)))
            return Map.of("rows", List.of(), "total", 0);
        StringBuilder where = new StringBuilder(" FROM wx_3d_scene WHERE tenant_id=? AND deleted=0 AND NOT " + TEMPLATE_SCENE);
        List<Object> parameters = new ArrayList<>(); parameters.add(actor.tenant());
        if (name != null && !name.isBlank()) { where.append(" AND name LIKE ?"); parameters.add("%" + name + "%"); }
        if (type != null && !type.isBlank()) { where.append(" AND scene_type=?"); parameters.add(type); }
        if (category != null && !category.isBlank()) { where.append(" AND scene_category=?"); parameters.add(category); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parameters.toArray());
        parameters.add(size); parameters.add((number - 1) * size);
        var rows = jdbc.queryForList("SELECT " + SUMMARY + where + " ORDER BY update_time DESC,id DESC LIMIT ? OFFSET ?", parameters.toArray());
        return Map.of("rows", rows.stream().map(this::summary).toList(), "total", total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> create(JsonNode body) {
        var actor = access.actor();
        String name = text(body, "name", 100), type = text(body, "sceneType", 16);
        if (!Set.of("local3d", "gis").contains(type)) throw bad("场景类型无效");
        long id = IdWorker.getId();
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,create_by) VALUES(?,?,?,?,?,?,?,?)",
                id, actor.tenant(), name, optional(body, "sceneDesc", 500), optional(body, "sceneCategory", 100), type, optional(body, "coverAssetId", 32), actor.user());
        // 首版空快照由服务端产生，四个域始终成套存在，后续保存只追加不可变修订。
        ObjectNode scene = json.createObjectNode().put("schemaVersion", 1).put("name", name).put("sceneType", type);
        scene.putObject("domainSlots").put("twoD", "present").put("threeD", "present"); scene.putObject("metadata");
        ObjectNode threeD = json.createObjectNode();
        ObjectNode base = threeD.putObject("baseConfig");
        ObjectNode coordinates = base.putObject("coordinateSystem");
        if ("gis".equals(type)) coordinates.put("kind", "wgs84").put("terrainEnabled", false).put("defaultLongitude", 104.0668).put("defaultLatitude", 30.5728).put("defaultHeight", 0);
        else coordinates.put("kind", "local").put("unit", "meter").put("upAxis", "Y").put("gridEnabled", true);
        base.putArray("gisLayers"); base.putObject("scene"); base.putObject("camera"); base.putObject("weather"); base.putObject("settings");
        for (String key : List.of("objects", "components", "layers", "cameraPaths")) threeD.putArray(key);
        ObjectNode streaming = threeD.putObject("streaming");
        for (String key : List.of("groups", "chunks", "chunkTargets")) streaming.putArray(key);
        ObjectNode twoD = json.createObjectNode(); twoD.putObject("canvas"); twoD.putArray("components"); twoD.putArray("layerTree");
        ObjectNode dependencies = json.createObjectNode();
        for (String key : List.of("resources", "dataBindings", "behaviors", "deviceBindings")) dependencies.putArray(key);
        insertRevision(actor, id, 1, 1, List.of(scene.toString(), threeD.toString(), twoD.toString(), dependencies.toString()), "initial", "0".repeat(64));
        return Map.of("sceneId", Long.toString(id), "sceneType", type, "revisionNo", 1);
    }

    public Map<String, Object> info(String id) {
        var actor = access.actor();
        var row = scene(id, actor, false);
        var result = summary(row);
        var revisions = jdbc.queryForList("SELECT * FROM wx_3d_scene_revision WHERE tenant_id=? AND scene_id=? AND revision_no=?", actor.tenant(), requireId(id), row.get("current_revision"));
        if (revisions.size() != 1) throw new ServiceException(500, "场景修订不完整");
        result.put("currentRevision", revision(revisions.get(0)));
        return result;
    }

    @Transactional
    public void edit(String id, JsonNode body) {
        var actor = access.actor(); var row = scene(id, actor, true);
        if (!Objects.equals(row.get("scene_type"), text(body, "sceneType", 16))) throw bad("场景类型不能修改");
        // 元数据修改也须尊重有效编辑租约，避免另一个页面重命名覆盖正在保存的场景。
        if (liveLock(row)) throw new ServiceException(409, "场景正在编辑，请退出编辑器后修改");
        jdbc.update("UPDATE wx_3d_scene SET name=?,scene_desc=?,scene_category=?,cover_asset_id=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                text(body, "name", 100), optional(body, "sceneDesc", 500), optional(body, "sceneCategory", 100), optional(body, "coverAssetId", 32), requireId(id), actor.tenant());
    }

    @Transactional
    public void remove(String id) {
        var actor = access.actor(); var row = scene(id, actor, true);
        if (liveLock(row)) throw new ServiceException(409, "场景正在编辑，请退出编辑器后删除");
        jdbc.update("UPDATE wx_3d_scene SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", requireId(id), actor.tenant());
    }

    @Transactional
    public Map<String, Object> lock(String id, String client) {
        var actor = access.actor(); var row = scene(id, actor, true); requireClient(client);
        if (liveLock(row) && (!Objects.equals(row.get("lock_user"), actor.user()) || !client.equals(row.get("lock_client"))))
            throw new ServiceException(409, "场景已由其他编辑页签锁定");
        String token = liveLock(row) ? (String) row.get("lock_token") : UUID.randomUUID().toString();
        Timestamp expiry = Timestamp.from(Instant.now().plusSeconds(1200));
        jdbc.update("UPDATE wx_3d_scene SET lock_user=?,lock_client=?,lock_token=?,lock_expire=? WHERE id=? AND tenant_id=?",
                actor.user(), client, token, expiry, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "lockToken", token, "locked", true, "baseRevisionNo", row.get("current_revision"), "expireTime", expiry.toInstant().toString());
    }

    @Transactional
    public Map<String, Object> heartbeat(String id, JsonNode body) {
        var actor = access.actor(); var row = scene(id, actor, true); requireLock(row, actor, text(body, "lockToken", 64));
        Timestamp expiry = Timestamp.from(Instant.now().plusSeconds(1200));
        jdbc.update("UPDATE wx_3d_scene SET lock_expire=? WHERE id=? AND tenant_id=?", expiry, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "locked", true, "expireTime", expiry.toInstant().toString());
    }

    @Transactional
    public void unlock(String id, String client) {
        var actor = access.actor(); scene(id, actor, true); requireClient(client);
        // 卸载重试只能释放本用户、本页签的租约，迟到的旧请求不能解除新页签的锁。
        jdbc.update("UPDATE wx_3d_scene SET lock_user=NULL,lock_client=NULL,lock_token=NULL,lock_expire=NULL WHERE id=? AND tenant_id=? AND lock_user=? AND lock_client=?",
                requireId(id), actor.tenant(), actor.user(), client);
    }

    @Transactional
    public Map<String, Object> save(JsonNode body) {
        var actor = access.actor(); String id = text(body, "sceneId", 19);
        String request = text(body, "requestId", 128), token = text(body, "lockToken", 64);
        if (!body.path("baseRevisionNo").isInt() || body.path("baseRevisionNo").asInt() < 1 || body.path("schemaVersion").asInt() != 1)
            throw bad("修订号或结构版本无效");
        List<String> packages = new ArrayList<>(); List<JsonNode> parsed = new ArrayList<>();
        for (String key : PACKAGE_KEYS) {
            String raw = text(body, key, 4 * 1024 * 1024);
            try { JsonNode value = json.readTree(raw); if (value == null || !value.isObject()) throw bad("四包必须为JSON对象"); parsed.add(value); }
            catch (java.io.IOException error) { throw bad("四包JSON格式无效"); }
            packages.add(raw);
        }
        var row = scene(id, actor, true);
        if (!Objects.equals(row.get("scene_type"), parsed.get(0).path("sceneType").asText()) ||
                !"present".equals(parsed.get(0).path("domainSlots").path("twoD").asText()) ||
                !"present".equals(parsed.get(0).path("domainSlots").path("threeD").asText())) throw bad("场景类型或四包域声明无效");
        String name = text(parsed.get(0), "name", 100);
        String hash = digest(body.path("baseRevisionNo").asText() + "\n" + String.join("\n", packages));
        var replay = jdbc.queryForList("SELECT * FROM wx_3d_scene_revision WHERE tenant_id=? AND scene_id=? AND request_id=?", actor.tenant(), requireId(id), request);
        if (!replay.isEmpty()) {
            var previous = replay.get(0);
            if (!hash.equals(previous.get("request_hash")) || !Objects.equals(previous.get("create_by"), actor.user())) throw new ServiceException(409, "保存请求号已绑定其他内容");
            return bookmark(id, previous);
        }
        requireLock(row, actor, token);
        int current = ((Number) row.get("current_revision")).intValue();
        if (current != body.path("baseRevisionNo").asInt()) return Map.of("sceneId", id, "conflict", true, "conflictRevisionNo", current);
        validateNodeTree(parsed.get(1), "gis".equals(row.get("scene_type")));
        long revision = insertRevision(actor, requireId(id), current + 1, 1, packages, request, hash);
        jdbc.update("UPDATE wx_3d_scene SET current_revision=?,name=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", current + 1, name, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "revisionId", Long.toString(revision), "revisionNo", current + 1, "lockVersion", current + 1, "conflict", false, "savedTime", Instant.now().toString());
    }

    /**
     * 将一次编辑器采集的四包快照写入独立模板场景和目录。
     * 模板目录使用独立表，避免旧文件型模板的 templateFileId 与场景修订身份混用。
     */
    @Transactional
    public Map<String, Object> saveAsTemplateFromSnapshot(JsonNode body) {
        var actor = access.actor();
        // 使用现有平台管理员和平台租户校验，角色相同的其他租户也不能另存运营模板。
        access.maintain(actor, "GLOBAL_SYSTEM", "0");
        String sourceId = text(body, "sourceSceneId", 19);
        String request = text(body, "requestId", 128);
        String name = text(body, body.has("name") ? "name" : "templateName", 100);
        String description = optional(body, body.has("sceneDesc") ? "sceneDesc" : "templateDesc", 500);
        String cover = optional(body, "coverAssetId", 32);
        String templateType = normalizeTemplateType(text(body, "templateType", 32));
        String category = text(body, "category", 100);
        String resourceBelong = optional(body, "resourceBelong", 16);
        if (!resourceBelong.isEmpty() && !"MINE".equals(resourceBelong)) throw bad("首期快照模板只允许归属当前租户");
        if (body.has("schemaVersion") && requiredInt(body, "schemaVersion") != 1) throw bad("四包结构版本无效");
        String scope = text(body, "saveScope", 16);
        int requestedRevision = requiredInt(body, "sourceRevisionNo");
        if (!Set.of("INDUSTRY", "EXHIBITION").contains(templateType)) throw bad("模板类型无效");
        if (!Set.of("twoD", "threeD", "all").contains(scope)) throw bad("保存范围无效");
        if (requestedRevision < 1) throw bad("来源修订号无效");
        // 字典由系统服务维护，先验证再锁定来源，避免远程调用延长场景行锁。
        String dictionary = "dim3d_template_" + templateType.toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(dictionaries.validateDictDataList(dictionary, List.of(category)).getCheckedData()))
            throw bad("模板分类无效");
        if (!cover.isEmpty()) throw bad("当前服务无法验证模板封面归属，请先移除封面");

        var source = scene(sourceId, actor, true);
        List<String> packages = snapshotPackages(body, scope, source.get("scene_type").toString(), name);
        // 请求摘要绑定所有落库字段，避免同号修改封面或描述时返回旧结果。
        var fingerprint = json.createArrayNode().add(sourceId).add(requestedRevision).add(templateType)
                .add(category).add(scope).add(name).add(description).add(cover);
        packages.forEach(fingerprint::add);
        String hash = digest(fingerprint.toString());
        var replay = jdbc.queryForList("SELECT * FROM wx_3d_scene_template WHERE tenant_id=? AND request_id=? AND deleted=0", actor.tenant(), request);
        if (!replay.isEmpty()) {
            var previous = replay.get(0);
            if (!hash.equals(previous.get("request_hash")) || !Objects.equals(previous.get("create_by"), actor.user()))
                throw new ServiceException(409, "模板请求号已绑定其他内容");
            return templateResult(previous);
        }
        int currentRevision = ((Number) source.get("current_revision")).intValue();
        if (requestedRevision != currentRevision) throw new ServiceException(409, "来源场景已产生新修订，请重新采集");

        long templateSceneId = IdWorker.getId();
        long templateId = IdWorker.getId();
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,create_by) VALUES(?,?,?,?,?,?,?,?)",
                templateSceneId, actor.tenant(), name, description, category, source.get("scene_type"), cover, actor.user());
        insertRevision(actor, templateSceneId, 1, 1, packages, request, hash);
        jdbc.update("INSERT INTO wx_3d_scene_template(id,tenant_id,template_scene_id,source_scene_id,template_name,template_desc,template_type,scene_type,category,resource_belong,save_scope,cover_asset_id,status,request_id,request_hash,create_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                templateId, actor.tenant(), templateSceneId, requireId(sourceId), name, description, templateType, source.get("scene_type"), category, "MINE", scope,
                cover, "ONLINE", request, hash, actor.user());
        return templateReceipt(Long.toString(templateId), Long.toString(templateSceneId), source.get("scene_type"), scope);
    }

    /**
     * 从快照模板创建独立场景。模板四包先在内存中完成编号映射，再与新场景主记录、首版修订一起落库。
     * 这样模板始终保持不可编辑，实例保存时也不会继续引用模板场景编号。
     */
    @Transactional
    public Map<String, Object> createFromTemplate(JsonNode body) {
        var actor = access.actor();
        String templateSceneId = text(body, "templateSceneId", 19);
        String request = text(body, "requestId", 110);
        String name = text(body, "name", 100);
        String description = optional(body, "sceneDesc", 500);
        String category = optional(body, "sceneCategory", 100);
        var template = templateBySceneId(templateSceneId, actor, true);
        if (!"ONLINE".equals(template.get("status"))) throw new ServiceException(409, "模板当前未上线");
        var revisions = jdbc.queryForList("SELECT * FROM wx_3d_scene_revision WHERE tenant_id=? AND scene_id=? AND revision_no=1",
                actor.tenant(), requireId(templateSceneId));
        if (revisions.size() != 1) throw new ServiceException(500, "模板场景修订不完整");
        var sourceRevision = revisions.get(0);
        String storedRequest = "TPL6:" + request;
        String requestHash = digest(templateSceneId + "\n" + name + "\n" + description + "\n" + category + "\n"
                + sourceRevision.get("request_hash"));
        var replay = jdbc.queryForList("SELECT scene_id,request_hash FROM wx_3d_scene_revision WHERE tenant_id=? AND request_id=?",
                actor.tenant(), storedRequest);
        if (!replay.isEmpty()) {
            var previous = replay.get(0);
            if (!requestHash.equals(previous.get("request_hash"))) throw new ServiceException(409, "实例化请求号已绑定其他内容");
            return instanceReceipt(previous.get("scene_id").toString(), templateSceneId, template.get("scene_type"));
        }
        long sceneId = IdWorker.getId();
        List<String> packages = instantiatePackages(sourceRevision, template.get("source_scene_id").toString(), templateSceneId, Long.toString(sceneId), name);
        jdbc.update("INSERT INTO wx_3d_scene(id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,create_by) VALUES(?,?,?,?,?,?,?,?)",
                sceneId, actor.tenant(), name, description.isEmpty() ? template.get("template_desc") : description,
                category.isEmpty() ? template.get("category") : category, template.get("scene_type"), template.get("cover_asset_id"), actor.user());
        long revision = insertRevision(actor, sceneId, 1, 1, packages, storedRequest, requestHash);
        return Map.of("sceneId", Long.toString(sceneId), "templateSceneId", templateSceneId, "sceneKind", "INSTANCE",
                "sceneType", template.get("scene_type"), "revisionId", Long.toString(revision), "revisionNo", 1);
    }

    /** 查询当前租户的快照模板目录，目录只返回摘要，不把四包正文带入分页结果。 */
    public PageResult<Map<String, Object>> templatePage(String name, String type, String category, String status, Integer number, Integer size) {
        var actor = access.actor();
        number = number == null ? 1 : number; size = size == null ? 10 : size;
        if (number < 1 || size < 1 || size > 100) throw bad("分页范围无效");
        StringBuilder where = new StringBuilder(" FROM wx_3d_scene_template WHERE tenant_id=? AND deleted=0");
        List<Object> parameters = new ArrayList<>(); parameters.add(actor.tenant());
        if (name != null && !name.isBlank()) { where.append(" AND template_name LIKE ?"); parameters.add("%" + name + "%"); }
        if (type != null && !type.isBlank()) { where.append(" AND template_type=?"); parameters.add(normalizeTemplateType(type)); }
        if (category != null && !category.isBlank()) { where.append(" AND category=?"); parameters.add(category); }
        if (status != null && !status.isBlank()) { where.append(" AND status=?"); parameters.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parameters.toArray());
        parameters.add(size); parameters.add((number - 1) * size);
        var rows = jdbc.queryForList("SELECT id,template_scene_id,source_scene_id,template_name,template_desc,template_type,scene_type,category,resource_belong,save_scope,cover_asset_id,status,update_time" + where + " ORDER BY update_time DESC,id DESC LIMIT ? OFFSET ?", parameters.toArray());
        return new PageResult<>(rows.stream().map(this::templateSummary).toList(), total == null ? 0L : total);
    }

    /** 兼容旧 /dim3d/template/page：在同一分页中投影旧文件型模板和场景快照模板。 */
    public PageResult<Map<String, Object>> templatePageCombined(String name, String type, String category, String resourceBelong,
                                                                  String status, Integer number, Integer size) {
        var actor = access.actor();
        number = number == null ? 1 : number; size = size == null ? 10 : size;
        if (number < 1 || size < 1 || size > 100) throw bad("分页范围无效");
        String normalizedType = normalizeTemplateType(type);
        // 数据库按数值编号排序，仅在返回时转字符串。旧目录继续按认证租户隔离。
        String old = """
                SELECT id,template_name,template_desc,template_type,category,resource_belong,
                       CONCAT('',cover_image_id) cover_image_id,template_file_id,status,NULL template_scene_id,
                       update_time,'LEGACY' record_kind
                FROM wx_3d_template WHERE del_flag='0' AND tenant_id=?
                """;
        String snapshot = """
                SELECT id,template_name,template_desc,template_type,category,resource_belong,
                       cover_asset_id,NULL,status,template_scene_id,update_time,'SNAPSHOT'
                FROM wx_3d_scene_template WHERE deleted=0 AND tenant_id=?
                """;
        StringBuilder where = new StringBuilder(" FROM (" + old + " UNION ALL " + snapshot + ") t WHERE 1=1");
        List<Object> parameters = new ArrayList<>(); parameters.add(Long.toString(actor.tenant())); parameters.add(actor.tenant());
        if (name != null && !name.isBlank()) { where.append(" AND template_name LIKE ?"); parameters.add("%" + name + "%"); }
        if (normalizedType != null && !normalizedType.isBlank()) { where.append(" AND template_type=?"); parameters.add(normalizedType); }
        if (category != null && !category.isBlank()) { where.append(" AND category=?"); parameters.add(category); }
        if (resourceBelong != null && !resourceBelong.isBlank()) { where.append(" AND resource_belong=?"); parameters.add(resourceBelong); }
        if (status != null && !status.isBlank()) { where.append(" AND status=?"); parameters.add(status); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters); pageParameters.add(size); pageParameters.add((number - 1L) * size);
        var rows = jdbc.queryForList("SELECT id,template_name,template_desc,template_type,category,resource_belong,cover_image_id,template_file_id,status,template_scene_id,update_time,record_kind" + where + " ORDER BY update_time DESC,id DESC,record_kind LIMIT ? OFFSET ?", pageParameters.toArray());
        return new PageResult<>(rows.stream().map(this::combinedTemplateSummary).toList(), total == null ? 0L : total);
    }

    /** 查询快照模板详情并回读独立四包；来源场景只返回身份，不暴露其当前正文。 */
    public Map<String, Object> templateInfo(String id) {
        var actor = access.actor();
        var row = template(id, actor, false);
        var result = templateSummary(row);
        var revisions = jdbc.queryForList("SELECT * FROM wx_3d_scene_revision WHERE tenant_id=? AND scene_id=? AND revision_no=1", actor.tenant(), row.get("template_scene_id"));
        if (revisions.size() != 1) throw new ServiceException(500, "模板场景修订不完整");
        result.put("currentRevision", revision(revisions.get(0)));
        return result;
    }

    @Transactional
    public void changeTemplateStatus(String id, String status) {
        if (!Set.of("ONLINE", "OFFLINE").contains(status)) throw bad("状态只能是ONLINE或OFFLINE");
        var actor = access.actor(); template(id, actor, true);
        jdbc.update("UPDATE wx_3d_scene_template SET status=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND deleted=0", status, requireId(id), actor.tenant());
    }

    @Transactional
    public void removeTemplate(String id) {
        var actor = access.actor(); var row = template(id, actor, true);
        jdbc.update("UPDATE wx_3d_scene_template SET deleted=1,status='OFFLINE',update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", requireId(id), actor.tenant());
        jdbc.update("UPDATE wx_3d_scene SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND deleted=0", row.get("template_scene_id"), actor.tenant());
    }

    /** 校验完整三维节点集合，保留原始四包文本，不重写业务扩展字段。 */
    private static void validateNodeTree(JsonNode threeD, boolean geospatial) {
        Map<String, String> parents = new HashMap<>();
        for (String key : List.of("objects", "components")) {
            JsonNode nodes = threeD.path(key);
            if (nodes.isMissingNode()) continue;
            if (!nodes.isArray()) throw bad("三维节点集合必须为数组");
            for (JsonNode node : nodes) {
                String id = text(node, "instanceId", 4 * 1024 * 1024);
                JsonNode parent = node.get("parentInstanceId");
                JsonNode order = node.get("sortNo");
                if (order != null && (!order.isIntegralNumber() || !order.canConvertToInt() || order.intValue() < 0))
                    throw bad("节点顺序必须为非负整数");
                if (parent != null && (!parent.isTextual() || parent.textValue().isBlank() || geospatial))
                    throw bad("父实例编号无效或当前场景不支持父子关系");
                if (parents.putIfAbsent(id, parent == null ? "" : parent.textValue()) != null)
                    throw bad("重复实例编号：" + id);
            }
        }
        // 复用路径集合并缓存已校验的节点，长链仍只线性访问，不递归。
        Set<String> done = new HashSet<>();
        Set<String> path = new HashSet<>();
        for (String id : parents.keySet()) {
            path.clear();
            String cursor = id;
            while (!cursor.isEmpty() && !done.contains(cursor)) {
                if (!path.add(cursor)) throw bad("父子关系包含循环");
                String parent = parents.get(cursor);
                if (parent == null) throw bad("父实例不存在：" + cursor);
                cursor = parent;
            }
            done.addAll(path);
        }
    }

    private long insertRevision(ArtResourceAccess.Actor actor, long id, int number, int schema, List<String> packages, String request, String hash) {
        long revision = IdWorker.getId();
        jdbc.update("INSERT INTO wx_3d_scene_revision(id,tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json,request_id,request_hash,create_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                revision, actor.tenant(), id, number, schema, packages.get(0), packages.get(1), packages.get(2), packages.get(3), request, hash, actor.user());
        return revision;
    }

    /** 校验并按保存范围生成四包；未选中的域写入标准空结构，避免模板误带入另一域内容。 */
    private List<String> snapshotPackages(JsonNode body, String scope, String sceneType, String templateName) {
        List<String> packages = new ArrayList<>();
        List<JsonNode> parsed = new ArrayList<>();
        for (String key : PACKAGE_KEYS) {
            String raw = text(body, key, 4 * 1024 * 1024);
            try {
                JsonNode value = json.readTree(raw);
                if (value == null || !value.isObject()) throw bad("四包必须为JSON对象");
                parsed.add(value); packages.add(raw);
            } catch (java.io.IOException error) { throw bad("四包JSON格式无效"); }
        }
        if (!sceneType.equals(parsed.get(0).path("sceneType").asText()) ||
                !"present".equals(parsed.get(0).path("domainSlots").path("twoD").asText()) ||
                !"present".equals(parsed.get(0).path("domainSlots").path("threeD").asText())) throw bad("场景类型或四包域声明无效");
        ((ObjectNode) parsed.get(0)).put("name", templateName);
        packages.set(0, parsed.get(0).toString());
        validateNodeTree(parsed.get(1), "gis".equals(sceneType));
        validateTemplateScope(parsed.get(1), parsed.get(2), parsed.get(3), scope);
        if ("twoD".equals(scope)) packages.set(1, emptyThreeD(sceneType));
        if ("threeD".equals(scope)) packages.set(2, emptyTwoD());
        return packages;
    }

    /** 单域模板只接收已裁剪的四包，防止被排除的域或依赖随快照进入目录。 */
    private void validateTemplateScope(JsonNode threeD, JsonNode twoD, JsonNode dependencies, String scope) {
        if ("all".equals(scope)) return;
        if ("twoD".equals(scope)) {
            ObjectNode empty = json.createObjectNode();
            empty.putObject("baseConfig");
            for (String key : List.of("objects", "components", "layers", "cameraPaths")) empty.putArray(key);
            ObjectNode streaming = empty.putObject("streaming");
            for (String key : List.of("groups", "chunks", "chunkTargets")) streaming.putArray(key);
            if (!empty.equals(threeD)) throw bad("仅二维模板的三维包必须为空");
        } else {
            ObjectNode empty = json.createObjectNode();
            empty.putObject("canvas"); empty.putArray("components"); empty.putArray("layerTree");
            if (!empty.equals(twoD)) throw bad("仅三维模板的二维包必须为空");
        }
        for (String key : List.of("resources", "dataBindings", "behaviors", "deviceBindings")) {
            JsonNode entries = dependencies.path(key);
            if (!entries.isArray()) throw bad("模板依赖集合必须为数组");
            for (JsonNode entry : entries) {
                if (!entry.isObject()) throw bad("模板依赖必须为对象");
                if ("resources".equals(key)) {
                    JsonNode owners = entry.path("owners");
                    if (!owners.isMissingNode() && !owners.isArray()) throw bad("资源依赖归属必须为数组");
                    for (JsonNode owner : owners) requireTemplateOwner(owner, scope);
                } else {
                    requireTemplateOwner(entry.path("owner"), scope);
                }
            }
        }
    }

    private static void requireTemplateOwner(JsonNode owner, String scope) {
        if (!scope.equals(owner.path("domain").asText())) throw bad("模板依赖归属超出保存范围");
    }

    private String emptyThreeD(String sceneType) {
        ObjectNode threeD = json.createObjectNode();
        ObjectNode base = threeD.putObject("baseConfig");
        base.putObject("coordinateSystem").put("kind", "gis".equals(sceneType) ? "wgs84" : "local");
        base.putArray("gisLayers"); base.putObject("scene"); base.putObject("camera"); base.putObject("weather"); base.putObject("settings");
        for (String key : List.of("objects", "components", "layers", "cameraPaths")) threeD.putArray(key);
        ObjectNode streaming = threeD.putObject("streaming");
        for (String key : List.of("groups", "chunks", "chunkTargets")) streaming.putArray(key);
        return threeD.toString();
    }

    private String emptyTwoD() {
        ObjectNode twoD = json.createObjectNode(); twoD.putObject("canvas"); twoD.putArray("components"); twoD.putArray("layerTree");
        return twoD.toString();
    }

    /** 复制模板四包并重建场景内节点、父级、图层和分块引用。 */
    private List<String> instantiatePackages(Map<String, Object> sourceRevision, String sourceSceneId, String templateSceneId, String sceneId, String name) {
        try {
            ObjectNode scene = (ObjectNode) json.readTree(sourceRevision.get("scene_json").toString());
            ObjectNode threeD = (ObjectNode) json.readTree(sourceRevision.get("three_d_json").toString());
            ObjectNode twoD = (ObjectNode) json.readTree(sourceRevision.get("two_d_json").toString());
            ObjectNode dependencies = (ObjectNode) json.readTree(sourceRevision.get("dependencies_json").toString());
            scene.put("name", name);
            Map<String, String> references = new LinkedHashMap<>();
            collectReferenceIds(threeD.path("objects"), "instanceId", references);
            collectReferenceIds(threeD.path("components"), "instanceId", references);
            collectReferenceIds(threeD.path("layers"), "id", references);
            remapReferences(threeD, references, sourceSceneId, templateSceneId, sceneId);
            remapReferences(twoD, references, sourceSceneId, templateSceneId, sceneId);
            remapReferences(dependencies, references, sourceSceneId, templateSceneId, sceneId);
            JsonNode chunks = threeD.path("streaming").path("chunks");
            if (chunks.isArray()) for (JsonNode chunk : chunks)
                if (chunk.isObject() && (sourceSceneId.equals(chunk.path("sceneId").asText()) || templateSceneId.equals(chunk.path("sceneId").asText())))
                    ((ObjectNode) chunk).put("sceneId", sceneId);
            return List.of(scene.toString(), threeD.toString(), twoD.toString(), dependencies.toString());
        } catch (java.io.IOException error) {
            throw new ServiceException(500, "模板四包无法解析");
        }
    }

    private void collectReferenceIds(JsonNode values, String key, Map<String, String> references) {
        if (!values.isArray()) return;
        for (JsonNode value : values) {
            String source = value.path(key).asText();
            if (!source.isBlank()) references.computeIfAbsent(source, ignored -> Long.toString(IdWorker.getId()));
        }
    }

    private void remapReferences(JsonNode node, Map<String, String> references, String sourceSceneId, String templateSceneId, String sceneId) {
        if (node.isArray()) {
            for (JsonNode child : node) remapReferences(child, references, sourceSceneId, templateSceneId, sceneId);
            return;
        }
        if (!node.isObject()) return;
        node.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isTextual()) {
                String text = value.asText();
                String mapped = references.get(text);
                if (mapped != null && Set.of("instanceId", "parentInstanceId", "objectId", "sourceObjectId", "targetObjectId",
                        "layerId", "sourceLayerId", "targetLayerId", "id").contains(entry.getKey()))
                    ((ObjectNode) node).put(entry.getKey(), mapped);
                else if ((sourceSceneId.equals(text) || templateSceneId.equals(text)) && "sceneId".equals(entry.getKey())) ((ObjectNode) node).put(entry.getKey(), sceneId);
            } else remapReferences(value, references, sourceSceneId, templateSceneId, sceneId);
        });
    }

    private Map<String, Object> templateResult(Map<String, Object> row) {
        return templateReceipt(row.get("id").toString(), row.get("template_scene_id").toString(), row.get("scene_type"), row.get("save_scope"));
    }

    private Map<String, Object> instanceReceipt(String sceneId, String templateSceneId, Object sceneType) {
        return Map.of("sceneId", sceneId, "templateSceneId", templateSceneId, "sceneKind", "INSTANCE", "sceneType", sceneType, "revisionNo", 1);
    }

    private Map<String, Object> templateReceipt(String id, String sceneId, Object sceneType, Object scope) {
        return Map.of("templateId", id, "templateSceneId", sceneId, "sceneId", sceneId,
                "sceneKind", "TEMPLATE", "sceneType", sceneType, "saveScope", scope);
    }

    private Map<String, Object> templateSummary(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id").toString()); out.put("templateId", row.get("id").toString()); out.put("templateSceneId", row.get("template_scene_id").toString());
        out.put("sourceSceneId", row.get("source_scene_id").toString()); out.put("templateName", row.get("template_name"));
        out.put("templateDesc", row.get("template_desc")); out.put("templateType", row.get("template_type"));
        out.put("sceneType", row.get("scene_type")); out.put("category", row.get("category"));
        out.put("resourceBelong", row.get("resource_belong")); out.put("saveScope", row.get("save_scope"));
        out.put("coverAssetId", row.get("cover_asset_id")); out.put("status", row.get("status"));
        out.put("updateTime", row.get("update_time") == null ? null : row.get("update_time").toString());
        return out;
    }

    private Map<String, Object> combinedTemplateSummary(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        String id = row.get("id").toString(); out.put("id", id); out.put("templateId", id);
        out.put("templateName", row.get("template_name")); out.put("templateDesc", row.get("template_desc"));
        out.put("templateType", row.get("template_type")); out.put("category", row.get("category"));
        out.put("resourceBelong", row.get("resource_belong")); out.put("coverImageId", row.get("cover_image_id"));
        out.put("templateFileId", Objects.toString(row.get("template_file_id"), null)); out.put("status", row.get("status"));
        out.put("templateSceneId", Objects.toString(row.get("template_scene_id"), null)); out.put("recordKind", row.get("record_kind"));
        out.put("updateTime", row.get("update_time") == null ? null : row.get("update_time").toString());
        return out;
    }

    private Map<String, Object> template(String id, ArtResourceAccess.Actor actor, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_scene_template WHERE id=? AND tenant_id=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), requireId(id), actor.tenant());
        if (rows.isEmpty()) throw new ServiceException(404, "模板不存在或当前身份不可见");
        return rows.get(0);
    }

    private Map<String, Object> templateBySceneId(String sceneId, ArtResourceAccess.Actor actor, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_scene_template WHERE template_scene_id=? AND tenant_id=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), requireId(sceneId), actor.tenant());
        if (rows.isEmpty()) throw new ServiceException(404, "模板不存在或当前身份不可见");
        return rows.get(0);
    }

    private static String normalizeTemplateType(String type) {
        if (type == null || type.isBlank()) return type;
        String value = type.trim();
        return value.startsWith("dim3d_template_") ? value.substring("dim3d_template_".length()).toUpperCase(Locale.ROOT) : value;
    }

    private Map<String, Object> scene(String id, ArtResourceAccess.Actor actor, boolean lock) {
        String projection = lock ? "*" : "*, CASE WHEN " + TEMPLATE_SCENE + " THEN 'TEMPLATE' ELSE 'INSTANCE' END scene_kind";
        String guard = lock ? " AND NOT " + TEMPLATE_SCENE + " FOR UPDATE" : "";
        var rows = jdbc.queryForList("SELECT " + projection + " FROM wx_3d_scene WHERE id=? AND tenant_id=? AND deleted=0" + guard, requireId(id), actor.tenant());
        if (rows.isEmpty()) throw new ServiceException(404, "场景不存在或当前身份不可见");
        return rows.get(0);
    }

    private Map<String, Object> summary(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sceneId", row.get("id").toString()); out.put("tenantId", row.get("tenant_id").toString()); out.put("name", row.get("name"));
        out.put("sceneDesc", row.get("scene_desc")); out.put("sceneCategory", row.get("scene_category")); out.put("sceneType", row.get("scene_type"));
        out.put("coverAssetId", row.get("cover_asset_id")); out.put("sceneKind", row.getOrDefault("scene_kind", "INSTANCE")); out.put("publishStatus", 0); out.put("updateTime", row.get("update_time").toString());
        return out;
    }

    private Map<String, Object> revision(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(bookmark(row.get("scene_id").toString(), row));
        out.put("schemaVersion", row.get("schema_version"));
        out.put("sceneJson", row.get("scene_json")); out.put("threeDJson", row.get("three_d_json")); out.put("twoDJson", row.get("two_d_json")); out.put("dependenciesJson", row.get("dependencies_json"));
        return out;
    }

    private Map<String, Object> bookmark(String id, Map<String, Object> row) {
        return Map.of("sceneId", id, "revisionId", row.get("id").toString(), "revisionNo", row.get("revision_no"), "lockVersion", row.get("revision_no"), "conflict", false, "savedTime", row.get("create_time").toString());
    }

    private static boolean liveLock(Map<String, Object> row) { return row.get("lock_expire") instanceof Timestamp time && time.toInstant().isAfter(Instant.now()); }
    private static void requireLock(Map<String, Object> row, ArtResourceAccess.Actor actor, String token) {
        if (!liveLock(row) || !Objects.equals(row.get("lock_user"), actor.user()) || !token.equals(row.get("lock_token"))) throw new ServiceException(409, "编辑锁已失效，请重新打开场景");
    }
    private static void requireClient(String value) { if (value == null || value.isBlank() || value.length() > 128) throw bad("编辑页签标识无效"); }
    private static long requireId(String value) {
        try { long id = Long.parseLong(value); if (id < 1 || !Long.toString(id).equals(value)) throw bad("场景编号无效"); return id; }
        catch (NumberFormatException error) { throw bad("场景编号无效"); }
    }
    private static String text(JsonNode body, String key, int maximum) {
        JsonNode value = body == null ? null : body.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum) throw bad(key + "格式无效");
        return value.asText();
    }
    private static String optional(JsonNode body, String key, int maximum) { return body.hasNonNull(key) && !body.path(key).asText().isBlank() ? text(body, key, maximum) : ""; }
    private static int requiredInt(JsonNode body, String key) {
        JsonNode value = body == null ? null : body.get(key);
        if (value == null || (!value.isIntegralNumber() && !value.isTextual())) throw bad(key + "格式无效");
        try { return Integer.parseInt(value.asText()); }
        catch (NumberFormatException error) { throw bad(key + "格式无效"); }
    }
    private static ServiceException bad(String message) { return new ServiceException(400, message); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
