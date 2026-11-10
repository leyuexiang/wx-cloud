package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.util.*;

/** 发布事务内固定受管资源的已提交元数据；不在持锁期间访问对象存储。 */
@Service
public class ScenePublicationDependencyService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceManifestVerifier parser;
    private static final Set<String> FORMATS = Set.of("unity.assetbundle.v1", "model.gltf.v1", "model.glb.v1");

    public record Pin(String key, long resource, long revision, String hash, String manifestHash,
                      String operation, String scope, String owner, String snapshot) {}
    private record Request(String key, long resource, Long revision, String hash, String manifestHash) {}

    /** 回退时以原发布引用覆盖缺省版本，避免历史回退跟随资源当前指针。 */
    public List<Pin> resolve(String dependencies, long tenant, String sourcePublication) {
        JsonNode resources = parse(dependencies).path("resources");
        if (!resources.isArray() || resources.size() > 256) throw invalid();
        if (resources.isEmpty()) return List.of();
        Map<String, Pin> previous = new HashMap<>();
        if (sourcePublication != null) {
            for (var row : jdbc.queryForList("SELECT * FROM wx_3d_scene_publication_resource WHERE publication_id=? AND tenant_id=?", sourcePublication, tenant)) {
                Pin pin = new Pin(row.get("resource_key").toString(), ((Number) row.get("resource_id")).longValue(),
                        ((Number) row.get("resource_revision")).longValue(), row.get("content_hash").toString(),
                        row.get("manifest_sha256").toString(), row.get("operation_id").toString(),
                        row.get("resource_scope").toString(), row.get("resource_tenant_id").toString(), text(row.get("snapshot_json")));
                previous.put(pin.key(), pin);
            }
        }
        List<Request> requests = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        SortedSet<Long> ids = new TreeSet<>();
        for (JsonNode item : resources) {
            String key = field(item, "resourceKey");
            if (!item.isObject() || !key.equals(key.trim()) || key.isBlank() || key.length() > 160
                    || !keys.add(key) || !"unity-model".equals(field(item, "kind"))) throw invalid();
            long id = positive(field(item, "assetId"));
            Long revision = item.has("revision") ? positive(field(item, "revision")) : null;
            String hash = optionalDigest(item, "contentHash"), manifestHash = optionalDigest(item, "manifestSha256");
            if (sourcePublication != null) {
                Pin pin = previous.get(key);
                if (pin == null || pin.resource() != id || revision != null && revision != pin.revision()
                        || hash != null && !hash.equals(pin.hash())
                        || manifestHash != null && !manifestHash.equals(pin.manifestHash())) throw invalid();
                revision = pin.revision(); hash = pin.hash(); manifestHash = pin.manifestHash();
            }
            requests.add(new Request(key, id, revision, hash, manifestHash));
            ids.add(id);
        }
        if (sourcePublication != null && previous.size() != requests.size()) throw invalid();
        // 有界批量查询并按编号加锁，与资源替换串行化，避免逐条查询与锁顺序倒置。
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> arguments = new ArrayList<>(ids);
        arguments.add(Long.toString(tenant));
        var rows = jdbc.queryForList("SELECT id,resource_scope,tenant_id,resource_type,current_revision FROM wx_3d_resource WHERE id IN ("
                + placeholders + ") AND del_flag='0' AND content_state='READY' AND ((resource_scope='GLOBAL_SYSTEM' AND tenant_id='0')"
                + " OR (resource_scope='TENANT_SHARED' AND tenant_id=?)) ORDER BY id FOR UPDATE", arguments.toArray());
        Map<Long, Map<String, Object>> current = new HashMap<>();
        for (var row : rows) current.put(((Number) row.get("id")).longValue(), row);
        if (current.size() != ids.size()) throw invalid();
        Map<String, Long> revisions = new HashMap<>();
        LinkedHashMap<String, Request> identities = new LinkedHashMap<>();
        for (Request request : requests) {
            var row = current.get(request.resource());
            if (!"MODEL".equals(row.get("resource_type"))) throw invalid();
            long revision = request.revision() == null ? ((Number) row.get("current_revision")).longValue() : request.revision();
            if (revision < 1) throw invalid();
            revisions.put(request.key(), revision);
            identities.put(request.resource() + ":" + revision, request);
        }
        List<Object> snapshotArguments = new ArrayList<>();
        List<String> predicates = new ArrayList<>();
        for (var request : identities.values()) {
            predicates.add("(resource_id=? AND committed_revision=?)");
            snapshotArguments.add(request.resource()); snapshotArguments.add(revisions.get(request.key()));
        }
        var snapshots = jdbc.queryForList("SELECT id,resource_id,committed_revision,resource_scope,tenant_id,after_snapshot FROM wx_3d_resource_operation"
                + " WHERE status='COMMITTED' AND (" + String.join(" OR ", predicates) + ")", snapshotArguments.toArray());
        Map<String, Map<String, Object>> committed = new HashMap<>();
        for (var row : snapshots) {
            String identity = row.get("resource_id") + ":" + row.get("committed_revision");
            if (committed.put(identity, row) != null) throw invalid();
        }
        List<Pin> result = new ArrayList<>();
        for (Request request : requests) {
            long revision = revisions.get(request.key());
            var row = current.get(request.resource());
            var operation = committed.get(request.resource() + ":" + revision);
            if (operation == null || !Objects.equals(row.get("resource_scope"), operation.get("resource_scope"))
                    || !Objects.equals(row.get("tenant_id"), operation.get("tenant_id"))) throw invalid();
            String saved = text(operation.get("after_snapshot"));
            JsonNode snapshot = parse(saved).path("resource");
            String hash = field(snapshot, "content_hash"), manifestHash = field(snapshot, "manifest_sha256");
            if (!Long.toString(request.resource()).equals(field(snapshot, "id"))
                    || !Long.toString(revision).equals(field(snapshot, "current_revision"))
                    || !"READY".equals(field(snapshot, "content_state")) || !"0".equals(field(snapshot, "del_flag"))
                    || !ArtResourceManifestVerifier.digest(hash) || !ArtResourceManifestVerifier.digest(manifestHash)
                    || request.hash() != null && !request.hash().equals(hash)
                    || request.manifestHash() != null && !request.manifestHash().equals(manifestHash)
                    || !FORMATS.contains(field(snapshot, "content_format")) || !"WebGL".equals(field(snapshot, "platform"))
                    || field(snapshot, "app_compatibility").isBlank() || field(snapshot, "app_compatibility").length() > 128) throw invalid();
            String prefix = field(snapshot, "storage_prefix"), path = field(snapshot, "manifest_path");
            try {
                if (!prefix.startsWith("resources/") || !prefix.endsWith("/")) throw invalid();
                ArtResourceManifestVerifier.requirePath(prefix.substring(0, prefix.length() - 1));
                ArtResourceManifestVerifier.requirePath(path);
            } catch (Exception error) { throw invalid(); }
            result.add(new Pin(request.key(), request.resource(), revision, hash, manifestHash,
                    operation.get("id").toString(), row.get("resource_scope").toString(), row.get("tenant_id").toString(), saved));
        }
        return result;
    }

    /** 发布、引用和当前指针共享调用方事务；不删除已撤销发布的历史引用。 */
    public void save(String publication, long tenant, List<Pin> pins) {
        if (pins.isEmpty()) return;
        jdbc.batchUpdate("INSERT INTO wx_3d_scene_publication_resource(id,publication_id,tenant_id,resource_key,resource_id,resource_revision,"
                + "content_hash,manifest_sha256,operation_id,resource_scope,resource_tenant_id,snapshot_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                pins, 256, (statement, pin) -> {
                    statement.setLong(1, IdWorker.getId()); statement.setString(2, publication); statement.setLong(3, tenant);
                    statement.setString(4, pin.key()); statement.setLong(5, pin.resource()); statement.setLong(6, pin.revision());
                    statement.setString(7, pin.hash()); statement.setString(8, pin.manifestHash()); statement.setString(9, pin.operation());
                    statement.setString(10, pin.scope()); statement.setString(11, pin.owner()); statement.setString(12, pin.snapshot());
                });
    }

    private JsonNode parse(String value) {
        try {
            JsonNode root = parser.parse(value.getBytes(StandardCharsets.UTF_8));
            if (!root.isObject()) throw invalid();
            return root;
        } catch (Exception error) { throw invalid(); }
    }
    private static String field(JsonNode node, String key) { return node.path(key).isTextual() ? node.path(key).textValue() : ""; }
    private static String optionalDigest(JsonNode node, String key) {
        if (!node.has(key)) return null;
        String value = field(node, key);
        if (!ArtResourceManifestVerifier.digest(value)) throw invalid();
        return value;
    }
    private static long positive(String value) {
        try { long id = Long.parseLong(value); if (id < 1 || !Long.toString(id).equals(value)) throw invalid(); return id; }
        catch (NumberFormatException error) { throw invalid(); }
    }
    private static String text(Object value) {
        try { return value instanceof Clob clob ? clob.getSubString(1, Math.toIntExact(clob.length()))
                : value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : Objects.toString(value, ""); }
        catch (Exception error) { throw invalid(); }
    }
    private static ServiceException invalid() { return new ServiceException(409, "发布资源依赖缺失、无权访问或固定版本无效"); }
}
