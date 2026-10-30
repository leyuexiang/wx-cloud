package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.Resource;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** JDBC专用范围查询：每次访问显式限定所有者，不修改全局租户拦截配置。 */
@Service
public class ArtResourceService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;
    @Resource private ArtResourceDownloadSigner signer;
    @Resource private ArtResourceObjectStore store;
    @Resource private ArtResourceManifestVerifier verifier;
    @Resource private ObjectMapper json;
    @Resource private PlatformTransactionManager transactions;
    private static final String VISIBLE = " ((resource_scope='GLOBAL_SYSTEM' AND tenant_id='0') OR (resource_scope='TENANT_SHARED' AND tenant_id=?)) ";
    private static final String SUMMARY = "id,resource_name,resource_type,resource_scope,current_revision,content_hash";

    public Map<String,Object> page(int number, int size) {
        var actor=access.actor();
        if(number<1 || number>1000 || size<1 || size>100) throw new ServiceException(400,"分页范围无效");
        String predicate=" FROM wx_dim3d_resource WHERE del_flag='0' AND content_state='READY' AND "+VISIBLE;
        String owner=Long.toString(actor.tenant());
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+predicate,Long.class,owner);
        var rows=jdbc.queryForList("SELECT "+SUMMARY+predicate+" ORDER BY id DESC LIMIT ? OFFSET ?",owner,size,(number-1)*size);
        return Map.of("list",rows.stream().map(this::summary).toList(),"total",total==null?0:total);
    }
    public Map<String,Object> current(String id) {
        var actor=access.actor();
        var row=visible(id,actor,false);
        if(!"READY".equals(row.get("content_state"))) throw ServiceException.of(NOT_VISIBLE);
        var out=summary(row); out.remove("resourceType");
        for(String key:List.of("manifest_sha256","content_format","platform","app_compatibility"))
            out.put(camel(key),row.get(key));
        out.putAll(signer.sign((String)row.get("storage_prefix"),(String)row.get("manifest_path")));
        return out;
    }

    /**
     * 为已固定内容的闭包文件签发地址。替换后的旧场景仍按原修订读取，续签也不跟随当前指针。
     * 每次先校验资源现时可见性，再核对同范围的已提交快照及清单成员，不能签发任意兄弟文件。
     */
    public Map<String,String> file(String id, String revision, String contentHash, String path) {
        var actor=access.actor();
        var row=visible(id,actor,false);
        if(!"READY".equals(row.get("content_state"))) throw ServiceException.of(NOT_VISIBLE);
        long pinnedRevision=revision(revision);
        if(pinnedRevision==0 || !ArtResourceManifestVerifier.digest(contentHash)) bad();
        ArtResourceManifestVerifier.requirePath(path);
        var snapshots=jdbc.queryForList("SELECT after_snapshot FROM wx_dim3d_resource_operation WHERE resource_id=? AND committed_revision=? AND status='COMMITTED' AND resource_scope=? AND tenant_id=?",
                id(id),pinnedRevision,row.get("resource_scope"),row.get("tenant_id"));
        if(snapshots.size()!=1 || snapshots.get(0).get("after_snapshot")==null) throw ServiceException.of(SNAPSHOT_UNAVAILABLE);
        Object saved=snapshots.get(0).get("after_snapshot");
        JsonNode snapshot;
        try {
            String text=saved instanceof java.sql.Clob clob?clob.getSubString(1,Math.toIntExact(clob.length())):
                saved instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):saved.toString();
            snapshot=verifier.parse(text.getBytes(StandardCharsets.UTF_8)).path("resource");
        } catch(ServiceException ex) { throw ex; }
        catch(Exception ex) { throw ServiceException.of(INVALID_CLOSURE); }
        if(!id.equals(snapshot.path("id").asText()) || !revision.equals(snapshot.path("current_revision").asText()) ||
            !contentHash.equals(snapshot.path("content_hash").asText()) || !"READY".equals(snapshot.path("content_state").asText()) ||
            !"0".equals(snapshot.path("del_flag").asText())) throw ServiceException.of(SNAPSHOT_UNAVAILABLE);
        String prefix=snapshot.path("storage_prefix").asText(), manifest=snapshot.path("manifest_path").asText();
        var checked=verifier.verify(store.readManifest(prefix,manifest),id,snapshot.path("manifest_sha256").asText(),manifest);
        if(!contentHash.equals(checked.contentHash())) throw ServiceException.of(CHECKSUM_MISMATCH);
        if(!path.equals(manifest) && checked.files().stream().noneMatch(file->file.path().equals(path)))
            throw ServiceException.of(NOT_VISIBLE);
        var signed=signer.sign(prefix,path);
        return Map.of("fileUrl",signed.get("manifestUrl"),"downloadExpiresAt",signed.get("downloadExpiresAt"));
    }
    public Map<String,Object> create(JsonNode body) {
        var actor=access.actor();
        String operation=text(body,"operationId",36);
        if(!operation.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) bad();
        String type=text(body,"operationType",16);
        if(!Set.of("CREATE","REPLACE","DELETE","RESTORE").contains(type)) bad();
        boolean creating=type.equals("CREATE");
        fields(body,creating?Set.of("operationId","operationType","resourceScope","metadata"):
            type.equals("RESTORE")?Set.of("operationId","operationType","resourceId","expectedRevision","snapshotOperationId"):
            Set.of("operationId","operationType","resourceId","expectedRevision"));
        if(creating) {
            String scope=text(body,"resourceScope",20);
            access.maintain(actor,scope,scope.equals("GLOBAL_SYSTEM")?"0":Long.toString(actor.tenant()));
            var meta=body.get("metadata");fields(meta,Set.of("resourceName","resourceType","assetName"));
            text(meta,"resourceName",150);text(meta,"assetName",150);
            if(!Set.of("MODEL","SPRITE","LIGHTING","COMPOSITION").contains(text(meta,"resourceType",20))) bad();
        } else { id(text(body,"resourceId",19)); revision(text(body,"expectedRevision",19)); }
        if(type.equals("RESTORE")) {
            String snapshot=text(body,"snapshotOperationId",36);
            try { UUID.fromString(snapshot); } catch(Exception e) { bad(); }
        }
        String hash=hash(body);
        // 并发同号插入失败后先回滚，再查询原操作，避免在已标记回滚的事务中继续写入。
        try { return new TransactionTemplate(transactions).execute(status -> {
            var existing=operationRows(operation,actor);
            if(!existing.isEmpty()) return replay(existing.get(0),actor,hash);
            long resource; String scope,owner; long expected=0;
            if(creating) {
                resource=IdWorker.getId(); scope=body.get("resourceScope").asText();
                owner=scope.equals("GLOBAL_SYSTEM")?"0":Long.toString(actor.tenant());
                var m=body.get("metadata");
                jdbc.update("INSERT INTO wx_dim3d_resource(id,resource_name,resource_type,asset_name,resource_relative_path,resource_scope,tenant_id,content_state,create_by) VALUES(?,?,?,?,?,?,?,'PENDING',?)",
                    resource,m.get("resourceName").asText(),m.get("resourceType").asText(),m.get("assetName").asText(),"",scope,owner,actor.user());
            } else {
                var row=visible(body.get("resourceId").asText(),actor,type.equals("RESTORE"));
                scope=(String)row.get("resource_scope"); owner=(String)row.get("tenant_id");
                access.maintain(actor,scope,owner);
                if(!"READY".equals(row.get("content_state"))) throw ServiceException.of(LEGACY_UNCLASSIFIED);
                resource=id(body.get("resourceId").asText());expected=revision(body.get("expectedRevision").asText());
                if(expected!=((Number)row.get("current_revision")).longValue()) throw ServiceException.of(REVISION_CONFLICT);
                if(type.equals("RESTORE") && jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource_operation WHERE id=? AND resource_id=? AND status='COMMITTED'",Long.class,body.get("snapshotOperationId").asText(),resource)==0)
                    throw ServiceException.of(SNAPSHOT_UNAVAILABLE);
            }
            String prefix="resources/"+scope+"/"+owner+"/"+resource+"/operations/"+operation+"/";
            jdbc.update("INSERT INTO wx_dim3d_resource_operation(id,resource_id,operation_type,resource_scope,tenant_id,operator_id,request_hash,expected_revision,storage_prefix,candidate_descriptor,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                operation,resource,type,scope,owner,actor.user(),hash,expected,prefix,
                type.equals("RESTORE")?"{\"snapshotOperationId\":\""+body.get("snapshotOperationId").asText()+"\"}":null,
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));
            return view(operationRows(operation,actor).get(0));
        }); } catch(DuplicateKeyException ex) {
            var rows=operationRows(operation,actor);
            if(rows.isEmpty()) throw ServiceException.of(NOT_VISIBLE);
            return replay(rows.get(0),actor,hash);
        }
    }
    public Map<String,Object> operation(String operation) {
        var actor=access.actor(); var rows=operationRows(operation,actor);
        if(rows.isEmpty()) throw ServiceException.of(NOT_VISIBLE);
        authorize(rows.get(0),actor);
        return view(rows.get(0));
    }
    public Map<String,Object> cancel(String operation) {
        operation(operation);
        int changed=jdbc.update("UPDATE wx_dim3d_resource_operation SET status='CANCELLED' WHERE id=? AND status='UPLOADING' AND expires_at>UTC_TIMESTAMP(3)",operation);
        if(changed==0 && !"CANCELLED".equals(operation(operation).get("status"))) throw ServiceException.of(INVALID_OPERATION_STATE);
        return operation(operation);
    }
    private Map<String,Object> visible(String value,ArtResourceAccess.Actor actor,boolean deleted) {
        var rows=jdbc.queryForList("SELECT * FROM wx_dim3d_resource WHERE id=? AND "+VISIBLE+(deleted?"":" AND del_flag='0'"),id(value),Long.toString(actor.tenant()));
        if(rows.isEmpty()) throw ServiceException.of(NOT_VISIBLE);
        return rows.get(0);
    }
    private List<Map<String,Object>> operationRows(String operation,ArtResourceAccess.Actor actor) {
        // 参数绑定防止操作编号成为SQL；返回内容只经操作者和范围复核后投影。
        return jdbc.queryForList("SELECT id,resource_id,operation_type,resource_scope,tenant_id,operator_id,request_hash,expected_revision,committed_revision,status,expires_at,error_code FROM wx_dim3d_resource_operation WHERE id=? AND operator_id=? AND "+VISIBLE,operation,actor.user(),Long.toString(actor.tenant()));
    }
    private void authorize(Map<String,Object> row,ArtResourceAccess.Actor actor) {
        if(((Number)row.get("operator_id")).longValue()!=actor.user()) throw ServiceException.of(NOT_VISIBLE);
        access.maintain(actor,(String)row.get("resource_scope"),(String)row.get("tenant_id"));
    }
    private Map<String,Object> replay(Map<String,Object> row,ArtResourceAccess.Actor actor,String hash) {
        authorize(row,actor);
        if(!hash.equals(row.get("request_hash"))) throw ServiceException.of(IDEMPOTENCY_CONFLICT);
        return view(row);
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var out=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","resource_id","operation_type","status","expected_revision"))
            out.put(key.equals("id")?"operationId":camel(key),String.valueOf(row.get(key)));
        if(row.get("committed_revision")!=null)out.put("committedRevision",row.get("committed_revision").toString());
        Object stored=row.get("expires_at");
        LocalDateTime expiry=stored instanceof Timestamp stamp?stamp.toLocalDateTime():(LocalDateTime)stored;
        out.put("expiresAt",expiry.toInstant(ZoneOffset.UTC).toString());
        if("UPLOADING".equals(row.get("status")) && expiry.isBefore(LocalDateTime.now(ZoneOffset.UTC))) out.put("status","EXPIRED");
        if(row.get("error_code")!=null)out.put("errorCode",row.get("error_code"));
        return out;
    }
    private Map<String,Object> summary(Map<String,Object> row) {
        var out=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","resource_name","resource_type","resource_scope","current_revision","content_hash"))
            out.put(key.equals("id")?"resourceId":camel(key),String.valueOf(row.get(key)));
        return out;
    }
    /** 旧写入只允许未纳入新流程的资源，查询不返回跨租户数据。 */
    public void guardLegacyResource(Long id) {
        if(id != null && jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource WHERE id=? AND (resource_scope IS NOT NULL OR content_state<>'LEGACY')",Long.class,id)>0)
            throw ServiceException.of(NOT_VISIBLE);
    }
    private static String camel(String value) {
        var out=new StringBuilder();boolean upper=false;
        for(char c:value.toCharArray()){if(c=='_')upper=true;else{out.append(upper?Character.toUpperCase(c):c);upper=false;}}
        return out.toString();
    }
    private String hash(JsonNode body) {
        try {
            var map=new TreeMap<String,Object>();body.fields().forEachRemaining(e->{
                if(e.getValue().isObject()) { var child=new TreeMap<String,String>();e.getValue().fields().forEachRemaining(v->child.put(v.getKey(),v.getValue().asText()));map.put(e.getKey(),child); }
                else map.put(e.getKey(),e.getValue().asText());
            });
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(map).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex) { throw new ServiceException(400,"请求无法规范化"); }
    }
    private static void fields(JsonNode node,Set<String> keys) {
        if(node==null || !node.isObject() || node.size()!=keys.size())bad();
        node.fieldNames().forEachRemaining(k->{if(!keys.contains(k))bad();});
    }
    private static String text(JsonNode node,String key,int length) {
        JsonNode n=node==null?null:node.get(key);
        if(n==null || !n.isTextual() || n.asText().isBlank() || n.asText().length()>length || n.asText().chars().anyMatch(c->c<32 || c==127))bad();
        return n.asText();
    }
    private static long id(String text) { long value=revision(text);if(value==0)bad();return value; }
    private static long revision(String text) {
        if(!text.matches("0|[1-9][0-9]{0,18}"))bad();
        try { return Long.parseLong(text); }catch(NumberFormatException ex){bad();return 0;}
    }
    private static void bad(){throw new ServiceException(400,"资源请求字段或格式无效");}
}
