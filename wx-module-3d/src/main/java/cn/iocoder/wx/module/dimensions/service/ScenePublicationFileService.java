package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.util.*;

/** 按票据冻结绑定读取资源，不创建后台身份，不追随资源当前修订。 */
@Service
public class ScenePublicationFileService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ScenePublicationTicketService tickets;
    @Resource private ArtResourceService resources;
    @Resource private ArtResourceManifestVerifier parser;
    @Resource private ArtResourceObjectStore store;
    @Resource private ArtResourceDownloadSigner signer;
    private static final String SELECT = "SELECT b.*,r.id AS visible_id,o.id AS committed_id,o.after_snapshot AS committed_snapshot "
            + "FROM wx_3d_scene_publication_resource b LEFT JOIN wx_3d_resource r ON r.id=b.resource_id AND r.resource_scope=b.resource_scope "
            + "AND r.tenant_id=b.resource_tenant_id AND r.del_flag='0' AND r.content_state='READY' "
            + "LEFT JOIN wx_3d_resource_operation o ON o.id=b.operation_id AND o.resource_id=b.resource_id AND o.committed_revision=b.resource_revision "
            + "AND o.resource_scope=b.resource_scope AND o.tenant_id=b.resource_tenant_id AND o.status='COMMITTED' "
            + "WHERE b.publication_id=? AND b.tenant_id=?";

    /** 只投影消费者需要的固定身份，内部前缀和操作快照不出现在响应中。 */
    public Map<String, Object> list(String token) {
        var grant = tickets.authorizeResources(token);
        var rows = jdbc.queryForList(SELECT + " ORDER BY b.resource_key LIMIT 257", grant.publicationId(), grant.tenant());
        if (rows.size() > 256) throw invalid();
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (var row : rows) {
            JsonNode snapshot = binding(row, grant.tenant());
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("resourceKey", row.get("resource_key"));
            value.put("resourceId", row.get("resource_id").toString());
            value.put("revision", row.get("resource_revision").toString());
            value.put("contentHash", row.get("content_hash"));
            value.put("manifestSha256", row.get("manifest_sha256"));
            for (String key : List.of("content_format", "platform", "app_compatibility", "manifest_path")) {
                String target = switch (key) { case "content_format" -> "contentFormat"; case "app_compatibility" -> "appCompatibility";
                    case "manifest_path" -> "manifestPath"; default -> key; };
                value.put(target, snapshot.path(key).asText());
            }
            list.add(value);
        }
        return Map.of("publicationId", grant.publicationId(), "resources", list);
    }

    /** 清单 IO 不持有数据库事务，结束后再校验准入，撤销后不得继续签发新地址。 */
    public Map<String, String> file(String token, String resourceKey, String path) {
        var grant = tickets.authorizeResources(token);
        if (resourceKey == null || resourceKey.isBlank() || resourceKey.length() > 160) throw new ServiceException(400, "资源键无效");
        ArtResourceManifestVerifier.requirePath(path);
        var rows = jdbc.queryForList(SELECT + " AND b.resource_key=?", grant.publicationId(), grant.tenant(), resourceKey);
        if (rows.size() != 1) throw new ServiceException(404, "发布中不存在该资源");
        var row = rows.get(0);
        JsonNode snapshot = binding(row, grant.tenant());
        String prefix = snapshot.path("storage_prefix").asText(), manifest = snapshot.path("manifest_path").asText();
        var files = resources.verifiedSnapshotFiles(snapshot, row.get("resource_id").toString(), row.get("content_hash").toString(), prefix, manifest);
        if (!path.equals(manifest)) {
            var member = files.stream().filter(file -> file.path().equals(path)).findFirst()
                    .orElseThrow(() -> new ServiceException(404, "文件不属于发布资源清单"));
            store.verifyAvailable(prefix, member);
        }
        var current = tickets.authorizeResources(token);
        if (!current.equals(grant)) throw new ServiceException(401, "访问票据已失效");
        var signed = signer.signUntil(prefix, path, current.expiresAt());
        return Map.of("fileUrl", signed.get("manifestUrl"), "downloadExpiresAt", signed.get("downloadExpiresAt"));
    }

    private JsonNode binding(Map<String, Object> row, long tenant) {
        String scope = Objects.toString(row.get("resource_scope"), ""), owner = Objects.toString(row.get("resource_tenant_id"), "");
        if (row.get("visible_id") == null || row.get("committed_id") == null
                || !("GLOBAL_SYSTEM".equals(scope) && "0".equals(owner) || "TENANT_SHARED".equals(scope) && Long.toString(tenant).equals(owner))) throw invalid();
        try {
            String saved = text(row.get("snapshot_json"));
            if (!saved.equals(text(row.get("committed_snapshot")))) throw invalid();
            JsonNode value = parser.parse(saved.getBytes(StandardCharsets.UTF_8)).path("resource");
            if (!row.get("resource_id").toString().equals(value.path("id").asText())
                    || !row.get("resource_revision").toString().equals(value.path("current_revision").asText())
                    || !row.get("content_hash").equals(value.path("content_hash").asText())
                    || !row.get("manifest_sha256").equals(value.path("manifest_sha256").asText())
                    || !"READY".equals(value.path("content_state").asText()) || !"0".equals(value.path("del_flag").asText())
                    || !"WebGL".equals(value.path("platform").asText()) || value.path("app_compatibility").asText().isBlank()) throw invalid();
            ArtResourceObjectStore.objectKey(value.path("storage_prefix").asText(), value.path("manifest_path").asText());
            return value;
        } catch (Exception error) { throw invalid(); }
    }

    private static String text(Object value) throws Exception {
        return value instanceof Clob clob ? clob.getSubString(1, Math.toIntExact(clob.length()))
                : value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : Objects.toString(value, "");
    }
    private static ServiceException invalid() { return new ServiceException(409, "发布固定资源引用已失效"); }
}
