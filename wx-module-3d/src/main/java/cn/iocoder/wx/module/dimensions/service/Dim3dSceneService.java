package cn.iocoder.wx.module.dimensions.service;

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
    private static final String SUMMARY = "id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,current_revision,update_time";
    private static final List<String> PACKAGE_KEYS = List.of("sceneJson", "threeDJson", "twoDJson", "dependenciesJson");

    public Map<String, Object> page(String name, String type, String category, String kind, Integer published, String tenant, int number, int size) {
        var actor = access.actor();
        if (number < 1 || number > 10000 || size < 1 || size > 100) throw bad("分页范围无效");
        // 模板和发布功能尚未接入，不将编辑草稿冒充模板或已发布内容。
        if ((kind != null && !"INSTANCE".equals(kind)) || (published != null && published != 0) ||
                (tenant != null && !tenant.isBlank() && !Long.toString(actor.tenant()).equals(tenant)))
            return Map.of("rows", List.of(), "total", 0);
        StringBuilder where = new StringBuilder(" FROM wx_dim3d_scene WHERE tenant_id=? AND deleted=0");
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
        jdbc.update("INSERT INTO wx_dim3d_scene(id,tenant_id,name,scene_desc,scene_category,scene_type,cover_asset_id,create_by) VALUES(?,?,?,?,?,?,?,?)",
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
        var revisions = jdbc.queryForList("SELECT * FROM wx_dim3d_scene_revision WHERE tenant_id=? AND scene_id=? AND revision_no=?", actor.tenant(), requireId(id), row.get("current_revision"));
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
        jdbc.update("UPDATE wx_dim3d_scene SET name=?,scene_desc=?,scene_category=?,cover_asset_id=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?",
                text(body, "name", 100), optional(body, "sceneDesc", 500), optional(body, "sceneCategory", 100), optional(body, "coverAssetId", 32), requireId(id), actor.tenant());
    }

    @Transactional
    public void remove(String id) {
        var actor = access.actor(); var row = scene(id, actor, true);
        if (liveLock(row)) throw new ServiceException(409, "场景正在编辑，请退出编辑器后删除");
        jdbc.update("UPDATE wx_dim3d_scene SET deleted=1,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", requireId(id), actor.tenant());
    }

    @Transactional
    public Map<String, Object> lock(String id, String client) {
        var actor = access.actor(); var row = scene(id, actor, true); requireClient(client);
        if (liveLock(row) && (!Objects.equals(row.get("lock_user"), actor.user()) || !client.equals(row.get("lock_client"))))
            throw new ServiceException(409, "场景已由其他编辑页签锁定");
        String token = liveLock(row) ? (String) row.get("lock_token") : UUID.randomUUID().toString();
        Timestamp expiry = Timestamp.from(Instant.now().plusSeconds(1200));
        jdbc.update("UPDATE wx_dim3d_scene SET lock_user=?,lock_client=?,lock_token=?,lock_expire=? WHERE id=? AND tenant_id=?",
                actor.user(), client, token, expiry, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "lockToken", token, "locked", true, "baseRevisionNo", row.get("current_revision"), "expireTime", expiry.toInstant().toString());
    }

    @Transactional
    public Map<String, Object> heartbeat(String id, JsonNode body) {
        var actor = access.actor(); var row = scene(id, actor, true); requireLock(row, actor, text(body, "lockToken", 64));
        Timestamp expiry = Timestamp.from(Instant.now().plusSeconds(1200));
        jdbc.update("UPDATE wx_dim3d_scene SET lock_expire=? WHERE id=? AND tenant_id=?", expiry, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "locked", true, "expireTime", expiry.toInstant().toString());
    }

    @Transactional
    public void unlock(String id, String client) {
        var actor = access.actor(); scene(id, actor, true); requireClient(client);
        // 卸载重试只能释放本用户、本页签的租约，迟到的旧请求不能解除新页签的锁。
        jdbc.update("UPDATE wx_dim3d_scene SET lock_user=NULL,lock_client=NULL,lock_token=NULL,lock_expire=NULL WHERE id=? AND tenant_id=? AND lock_user=? AND lock_client=?",
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
        var replay = jdbc.queryForList("SELECT * FROM wx_dim3d_scene_revision WHERE tenant_id=? AND scene_id=? AND request_id=?", actor.tenant(), requireId(id), request);
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
        jdbc.update("UPDATE wx_dim3d_scene SET current_revision=?,name=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=?", current + 1, name, requireId(id), actor.tenant());
        return Map.of("sceneId", id, "revisionId", Long.toString(revision), "revisionNo", current + 1, "lockVersion", current + 1, "conflict", false, "savedTime", Instant.now().toString());
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
        jdbc.update("INSERT INTO wx_dim3d_scene_revision(id,tenant_id,scene_id,revision_no,schema_version,scene_json,three_d_json,two_d_json,dependencies_json,request_id,request_hash,create_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                revision, actor.tenant(), id, number, schema, packages.get(0), packages.get(1), packages.get(2), packages.get(3), request, hash, actor.user());
        return revision;
    }

    private Map<String, Object> scene(String id, ArtResourceAccess.Actor actor, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM wx_dim3d_scene WHERE id=? AND tenant_id=? AND deleted=0" + (lock ? " FOR UPDATE" : ""), requireId(id), actor.tenant());
        if (rows.isEmpty()) throw new ServiceException(404, "场景不存在或当前身份不可见");
        return rows.get(0);
    }

    private Map<String, Object> summary(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sceneId", row.get("id").toString()); out.put("tenantId", row.get("tenant_id").toString()); out.put("name", row.get("name"));
        out.put("sceneDesc", row.get("scene_desc")); out.put("sceneCategory", row.get("scene_category")); out.put("sceneType", row.get("scene_type"));
        out.put("coverAssetId", row.get("cover_asset_id")); out.put("sceneKind", "INSTANCE"); out.put("publishStatus", 0); out.put("updateTime", row.get("update_time").toString());
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
    private static ServiceException bad(String message) { return new ServiceException(400, message); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
