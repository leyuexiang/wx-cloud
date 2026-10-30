package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 文件IO在事务外，最终指针与回执在短事务内提交；每次验证使用独立冻结前缀。 */
@Service
public class ArtResourceTransactionService {
    @Resource private JdbcTemplate jdbc;
    @Resource private ArtResourceAccess access;
    @Resource private ArtResourceService resources;
    @Resource private ArtResourceUploadSigner signer;
    @Resource private ArtResourceObjectStore store;
    @Resource private ArtResourceManifestVerifier verifier;
    @Resource private ArtResourceClosureVerifier closure;
    @Resource private ObjectMapper json;
    @Resource private PlatformTransactionManager transactions;
    private static final String VISIBLE=" ((resource_scope='GLOBAL_SYSTEM' AND tenant_id='0') OR (resource_scope='TENANT_SHARED' AND tenant_id=?)) ";
    private static final List<String> POINTER=List.of("storage_prefix","manifest_path","manifest_sha256","content_hash","content_format","platform","app_compatibility","content_state","del_flag");
    private static final List<String> DIRECTORIES=List.of("wx_dim3d_model","wx_dim3d_icon","wx_dim3d_template");
    private <T>T tx(java.util.function.Supplier<T> work){return new TransactionTemplate(transactions).execute(status->work.get());}

    public Map<String,Object> uploadTargets(String operation,JsonNode body){
        var actor=access.actor();
        exact(body,Set.of("files"));JsonNode rows=body.get("files");
        if(!rows.isArray()||rows.isEmpty()||rows.size()>4096)bad();
        TreeMap<String,JsonNode> sorted=new TreeMap<>();long total=0;
        for(JsonNode row:rows){
            exact(row,Set.of("path","size","sha256"));String path=text(row,"path");ArtResourceManifestVerifier.requirePath(path);
            if(!row.path("size").isIntegralNumber() || !row.path("size").canConvertToLong())bad();
            long size=row.get("size").asLong();
            if(size<1||size>ArtResourceManifestVerifier.MAX_FILE_BYTES||!ArtResourceManifestVerifier.digest(text(row,"sha256"))||sorted.putIfAbsent(path,row)!=null)bad();
            total+=size;if(total>ArtResourceManifestVerifier.MAX_CLOSURE_BYTES+ArtResourceManifestVerifier.MAX_MANIFEST_BYTES)bad();
        }
        ArrayNode plan=json.createArrayNode();sorted.values().forEach(plan::add);
        String prefix=tx(()->{
            var op=operation(operation,actor,true);live(op);
            if(!Set.of("CREATE","REPLACE").contains(op.get("operation_type"))||!Set.of("UPLOADING","FAILED").contains(op.get("status")))throw ServiceException.of(INVALID_OPERATION_STATE);
            ObjectNode state=state(op);
            if(state.has("filePlan")&&!state.get("filePlan").equals(plan))throw ServiceException.of(IDEMPOTENCY_CONFLICT);
            state.set("filePlan",plan);save(operation,state);
            return staging(op);
        });
        List<Map<String,Object>> targets=new ArrayList<>();
        for(JsonNode row:plan)targets.add(Map.of("path",row.get("path").asText(),"url",signer.sign(prefix,row.get("path").asText()),"method","PUT","requiredHeaders",Map.of("Content-Type","application/octet-stream")));
        return Map.of("operationId",operation,"expiresAt",Instant.now().plusSeconds(300).toString(),"targets",targets);
    }
    public Map<String,Object> commit(String operation,JsonNode body){
        var actor=access.actor();
        // 绑定候选载荷后才释放锁做IO；重复VERIFYING请求只返回进度。
        Map<String,Object> reserved=tx(()->{
            var op=operation(operation,actor,true);ObjectNode state=state(op);
            String type=(String)op.get("operation_type");
            if(Set.of("CREATE","REPLACE").contains(type)){
                exact(body,Set.of("manifestPath","manifestSha256","contentHash","contentFormat","platform","appCompatibility"));
                ArtResourceManifestVerifier.requirePath(text(body,"manifestPath"));
                if(!ArtResourceManifestVerifier.digest(text(body,"manifestSha256"))||!ArtResourceManifestVerifier.digest(text(body,"contentHash")))bad();
                if(!"unity.assetbundle.v1".equals(text(body,"contentFormat"))||!"WebGL".equals(text(body,"platform"))||text(body,"appCompatibility").length()>128)bad();
                if(!state.has("filePlan"))throw ServiceException.of(INVALID_OPERATION_STATE);
            }else exact(body,Set.of());
            if(state.has("commit")&&!state.get("commit").equals(body))throw ServiceException.of(IDEMPOTENCY_CONFLICT);
            if("COMMITTED".equals(op.get("status")))return Map.of("replay",true);
            live(op);
            if("VERIFYING".equals(op.get("status")) && state.path("leaseUntil").asLong(Long.MAX_VALUE)>Instant.now().getEpochSecond())return Map.of("replay",true);
            if(!Set.of("UPLOADING","FAILED","VERIFYING").contains(op.get("status")))throw ServiceException.of(INVALID_OPERATION_STATE);
            String attempt=UUID.randomUUID().toString();state.set("commit",body.deepCopy());state.put("attempt",attempt);state.put("leaseUntil",Instant.now().plusSeconds(900).getEpochSecond());
            save(operation,state);jdbc.update("UPDATE wx_dim3d_resource_operation SET status='VERIFYING',error_code=NULL WHERE id=?",operation);
            var result=new HashMap<>(op);result.put("state",state);result.put("attempt",attempt);return result;
        });
        if(reserved.containsKey("replay"))return resources.operation(operation);
        try{
            String type=(String)reserved.get("operation_type");Map<String,Object> pointer;
            ObjectNode state=(ObjectNode)reserved.get("state");
            if(type.equals("DELETE"))pointer=Map.of();
            else if(type.equals("RESTORE"))pointer=restore(reserved,state,actor);
            else pointer=validateAndFreeze(reserved,state,body);
            boolean committed=tx(()->finish(operation,actor,reserved,pointer));
            if(!committed)throw ServiceException.of(REVISION_CONFLICT);
            return resources.operation(operation);
        }catch(ServiceException ex){
            tx(()->{var op=operation(operation,actor,true);if("VERIFYING".equals(op.get("status"))&&state(op).path("attempt").asText().equals(reserved.get("attempt")))jdbc.update("UPDATE wx_dim3d_resource_operation SET status='FAILED',error_code=? WHERE id=?",String.valueOf(ex.getCode()),operation);return null;});
            throw ex;
        }catch(RuntimeException ex){
            // 数据库不可用时保持旧指针；无法写失败回执则由验证租约允许稍后恢复。
            try{tx(()->{var op=operation(operation,actor,true);if("VERIFYING".equals(op.get("status"))&&state(op).path("attempt").asText().equals(reserved.get("attempt")))jdbc.update("UPDATE wx_dim3d_resource_operation SET status='FAILED',error_code=? WHERE id=?",String.valueOf(RETRYABLE_DEPENDENCY.getCode()),operation);return null;});}catch(RuntimeException unavailable){ /* 数据库恢复后依赖租约重试，不伪造成功回执。 */ }
            var failure=ServiceException.of(RETRYABLE_DEPENDENCY);failure.initCause(ex);throw failure;
        }
    }
    private Map<String,Object> validateAndFreeze(Map<String,Object> op,ObjectNode state,JsonNode request){
        String stage=staging(op),path=request.get("manifestPath").asText();
        byte[] bytes=store.readManifest(stage,path);
        var checked=verifier.verify(bytes,op.get("resource_id").toString(),request.get("manifestSha256").asText(),path);
        if(!checked.contentHash().equals(request.get("contentHash").asText())||!checked.compatibility().equals(request.get("appCompatibility").asText()))throw ServiceException.of(CHECKSUM_MISMATCH);
        Map<String,ArtResourceManifestVerifier.FileSpec> expected=new TreeMap<>();checked.files().forEach(file->expected.put(file.path(),file));
        expected.put(path,new ArtResourceManifestVerifier.FileSpec(path,bytes.length,checked.manifestSha256()));
        if(state.get("filePlan").size()!=expected.size())throw ServiceException.of(INVALID_CLOSURE);
        for(JsonNode row:state.get("filePlan")){
            var file=expected.get(row.get("path").asText());
            if(file==null||file.size()!=row.get("size").asLong()||!file.sha256().equals(row.get("sha256").asText()))throw ServiceException.of(INVALID_CLOSURE);
        }
        String frozen=op.get("storage_prefix")+"frozen/"+op.get("attempt")+"/";
        store.freeze(stage,frozen,List.copyOf(expected.values()));
        // 检查冻结后的JSON关联，不能依赖可能仍可写入的暂存清单。
        closure.verify(frozen,checked);
        Map<String,Object> pointer=new HashMap<>();pointer.put("storage_prefix",frozen);pointer.put("manifest_path",path);pointer.put("manifest_sha256",checked.manifestSha256());pointer.put("content_hash",checked.contentHash());pointer.put("content_format","unity.assetbundle.v1");pointer.put("platform","WebGL");pointer.put("app_compatibility",checked.compatibility());pointer.put("content_state","READY");pointer.put("del_flag","0");pointer.put("kind",checked.kind());return pointer;
    }
    private Map<String,Object> restore(Map<String,Object> op,ObjectNode state,ArtResourceAccess.Actor actor){
        var rows=jdbc.queryForList("SELECT before_snapshot,after_snapshot,operation_type FROM wx_dim3d_resource_operation WHERE id=? AND resource_id=? AND status='COMMITTED' AND "+VISIBLE,text(state,"snapshotOperationId"),op.get("resource_id"),Long.toString(actor.tenant()));
        if(rows.isEmpty())throw ServiceException.of(SNAPSHOT_UNAVAILABLE);
        var row=rows.get(0);JsonNode snapshot=parse(row.get("operation_type").equals("DELETE")?row.get("before_snapshot"):row.get("after_snapshot"));
        JsonNode resource=snapshot.get("resource");if(resource==null||!"READY".equals(resource.path("content_state").asText()))throw ServiceException.of(SNAPSHOT_UNAVAILABLE);
        String prefix=text(resource,"storage_prefix"),path=text(resource,"manifest_path");
        byte[] bytes=store.readManifest(prefix,path);var checked=verifier.verify(bytes,op.get("resource_id").toString(),text(resource,"manifest_sha256"),path);
        for(var file:checked.files())store.verifyObject(ArtResourceObjectStore.objectKey(prefix,file.path()),file);
        closure.verify(prefix,checked);
        Map<String,Object> result=new HashMap<>();for(String key:POINTER)result.put(key,resource.path(key).asText());result.put("del_flag","0");result.put("directories",snapshot.path("directories"));return result;
    }
    private boolean finish(String operation,ArtResourceAccess.Actor actor,Map<String,Object> reserved,Map<String,Object> pointer){
        var resourceRows=jdbc.queryForList("SELECT * FROM wx_dim3d_resource WHERE id=? AND "+VISIBLE+" FOR UPDATE",reserved.get("resource_id"),Long.toString(actor.tenant()));
        if(resourceRows.isEmpty())throw ServiceException.of(NOT_VISIBLE);
        var resource=resourceRows.get(0);var op=operation(operation,actor,true);live(op);
        if(!"VERIFYING".equals(op.get("status"))||!state(op).path("attempt").asText().equals(reserved.get("attempt")))throw ServiceException.of(INVALID_OPERATION_STATE);
        access.maintain(actor,(String)resource.get("resource_scope"),(String)resource.get("tenant_id"));
        if(!Objects.equals(resource.get("resource_scope"),op.get("resource_scope"))||!Objects.equals(resource.get("tenant_id"),op.get("tenant_id")))throw ServiceException.of(SCOPE_CHANGE_FORBIDDEN);
        long revision=((Number)resource.get("current_revision")).longValue();String type=(String)op.get("operation_type");
        if(revision!=((Number)op.get("expected_revision")).longValue()){
            jdbc.update("UPDATE wx_dim3d_resource_operation SET status='CONFLICT',error_code=? WHERE id=?",String.valueOf(REVISION_CONFLICT.getCode()),operation);return false;
        }
        if(revision==Long.MAX_VALUE)throw ServiceException.of(REVISION_CONFLICT);
        if(!type.equals("RESTORE")&&!"0".equals(resource.get("del_flag")))throw ServiceException.of(RESOURCE_DELETED);
        if(pointer.containsKey("kind")&&!pointer.get("kind").toString().equalsIgnoreCase(resource.get("resource_type").toString()))throw ServiceException.of(INVALID_CLOSURE);
        ObjectNode before=snapshot(resource);
        if(type.equals("DELETE")){
            jdbc.update("UPDATE wx_dim3d_resource SET del_flag='1',current_revision=?,current_operation_id=?,update_by=? WHERE id=?",revision+1,operation,actor.user(),resource.get("id"));
            for(String table:DIRECTORIES)jdbc.update("UPDATE "+table+" SET del_flag='1',status='OFFLINE' WHERE resource_id=? AND tenant_id=?",resource.get("id"),resource.get("tenant_id"));
        }else{
            jdbc.update("UPDATE wx_dim3d_resource SET storage_prefix=?,manifest_path=?,manifest_sha256=?,content_hash=?,content_format=?,platform=?,app_compatibility=?,content_state='READY',del_flag='0',current_revision=?,current_operation_id=?,update_by=? WHERE id=?",
                pointer.get("storage_prefix"),pointer.get("manifest_path"),pointer.get("manifest_sha256"),pointer.get("content_hash"),pointer.get("content_format"),pointer.get("platform"),pointer.get("app_compatibility"),revision+1,operation,actor.user(),resource.get("id"));
            if(type.equals("RESTORE")&&pointer.get("directories") instanceof JsonNode directories)for(String table:DIRECTORIES)for(JsonNode row:directories.path(table))
                jdbc.update("UPDATE "+table+" SET del_flag=?,status=? WHERE id=? AND resource_id=? AND tenant_id=?",row.path("del_flag").asText(),row.path("status").asText(),row.path("id").asText(),resource.get("id"),resource.get("tenant_id"));
        }
        var after=jdbc.queryForMap("SELECT * FROM wx_dim3d_resource WHERE id=?",resource.get("id"));
        // 数据库事务同时写成功回执，响应丢失后查询原操作不会重复递增修订。
        jdbc.update("UPDATE wx_dim3d_resource_operation SET status='COMMITTED',committed_revision=?,before_snapshot=?,after_snapshot=?,error_code=NULL WHERE id=?",revision+1,write(before),write(snapshot(after)),operation);
        return true;
    }
    private ObjectNode snapshot(Map<String,Object> resource){
        ObjectNode root=json.createObjectNode(),entry=json.createObjectNode(),dirs=json.createObjectNode();
        for(String key:POINTER)if(resource.get(key)==null)entry.putNull(key);else entry.put(key,resource.get(key).toString());
        entry.put("id",resource.get("id").toString());entry.put("current_revision",resource.get("current_revision").toString());root.set("resource",entry);
        for(String table:DIRECTORIES){ArrayNode list=json.createArrayNode();for(var row:jdbc.queryForList("SELECT id,del_flag,status FROM "+table+" WHERE resource_id=? AND tenant_id=? ORDER BY id",resource.get("id"),resource.get("tenant_id"))){ObjectNode item=json.createObjectNode();row.forEach((key,value)->item.put(key.toLowerCase(Locale.ROOT),String.valueOf(value)));list.add(item);}dirs.set(table,list);}root.set("directories",dirs);return root;
    }
    private Map<String,Object> operation(String id,ArtResourceAccess.Actor actor,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM wx_dim3d_resource_operation WHERE id=? AND operator_id=? AND "+VISIBLE+(lock?" FOR UPDATE":""),id,actor.user(),Long.toString(actor.tenant()));
        if(rows.isEmpty())throw ServiceException.of(NOT_VISIBLE);var row=rows.get(0);access.maintain(actor,(String)row.get("resource_scope"),(String)row.get("tenant_id"));Map<String,Object> normalized=new HashMap<>();row.forEach((key,value)->normalized.put(key.toLowerCase(Locale.ROOT),value));return normalized;
    }
    private static String staging(Map<String,Object> op){return "resources/staging/"+op.get("resource_scope")+"/"+op.get("tenant_id")+"/"+op.get("resource_id")+"/"+op.get("id")+"/";}
    private ObjectNode state(Map<String,Object> op){return op.get("candidate_descriptor")==null?json.createObjectNode():(ObjectNode)parse(op.get("candidate_descriptor"));}
    private JsonNode parse(Object value){try{String text=value instanceof java.sql.Clob clob?clob.getSubString(1,Math.toIntExact(clob.length())):value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value.toString();return json.readTree(text);}catch(Exception ex){throw ServiceException.of(INVALID_CLOSURE);}}
    private void save(String operation,ObjectNode state){jdbc.update("UPDATE wx_dim3d_resource_operation SET candidate_descriptor=? WHERE id=?",write(state),operation);}
    private String write(JsonNode node){try{return json.writeValueAsString(node);}catch(Exception ex){throw ServiceException.of(INVALID_CLOSURE);}}
    private static void live(Map<String,Object> op){Object value=op.get("expires_at");LocalDateTime expiry=value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;if(!expiry.isAfter(LocalDateTime.now(ZoneOffset.UTC)))throw ServiceException.of(OPERATION_EXPIRED);}
    private static String text(JsonNode node,String key){JsonNode n=node.get(key);if(n==null||!n.isTextual()||n.asText().isBlank())bad();return n.asText();}
    private static void exact(JsonNode node,Set<String> fields){if(node==null||!node.isObject()||node.size()!=fields.size())bad();node.fieldNames().forEachRemaining(key->{if(!fields.contains(key))bad();});}
    private static void bad(){throw new ServiceException(400,"资源提交或上传计划格式无效");}
}
