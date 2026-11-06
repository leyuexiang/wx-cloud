package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 导入接收侧：只负责可信创建、独立原文件目标及持久化入队，不伪造转换成功。 */
@Service
public class ModelImportService {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private ArtResourceAccess access;
    @Resource private ArtResourceService resources;
    @Resource private ArtResourceUploadSigner signer;
    @Resource private ObjectMapper json;
    private static final Set<String> RUNNING=Set.of("QUEUED","VERIFYING_SOURCE","CONVERTING","VALIDATING","PUBLISHING");

    public ModelImportStatusView create(JsonNode body) {
        String type=text(body,"operationType",16);
        boolean creating="CREATE".equals(type);
        if(!creating && !"REPLACE".equals(type)) bad();
        fields(body,creating?Set.of("operationId","operationType","resourceScope","metadata","targetFormat","source"):
                Set.of("operationId","operationType","resourceId","expectedRevision","targetFormat","source"));
        operationId(text(body,"operationId",36));
        String format=text(body,"targetFormat",4);
        if(!Set.of("gltf","glb").contains(format)) bad();
        JsonNode source=body.get("source");fields(source,Set.of("size","sha256"));
        String size=text(source,"size",10),hash=text(source,"sha256",64);
        if(!size.matches("[1-9][0-9]{0,9}") || !ArtResourceManifestVerifier.digest(hash)) bad();
        long bytes=Long.parseLong(size);if(bytes>524288000) bad();
        ObjectNode operation=((ObjectNode)body).deepCopy();operation.remove(List.of("source","targetFormat"));
        if(creating) {
            fields(body.get("metadata"),Set.of("resourceName","assetName"));
            text(body.get("metadata"),"resourceName",150);text(body.get("metadata"),"assetName",150);
            ((ObjectNode)operation.get("metadata")).put("resourceType","MODEL");
        } else {
            positiveLong(text(body,"resourceId",19));positiveLong(text(body,"expectedRevision",19));
        }
        // 重复插入在外层事务回滚之后重读，不能在已失败的事务里继续返回成功。
        var template=new TransactionTemplate(transactions);
        try { return template.execute(status->createInside(operation,format,bytes,hash)); }
        catch(DuplicateKeyException ex) { return template.execute(status->createInside(operation,format,bytes,hash)); }
    }
    private ModelImportStatusView createInside(ObjectNode body,String format,long size,String hash) {
        var op=resources.createModelImport(body);String id=(String)op.get("operationId");
        var operation=lockOperation(id);
        var rows=jdbc.queryForList("SELECT * FROM wx_3d_resource_import WHERE operation_id=? FOR UPDATE",id);
        if(rows.isEmpty()) jdbc.update("INSERT INTO wx_3d_resource_import(operation_id,resource_id,target_format,source_size,source_sha256) VALUES(?,?,?,?,?)",
                id,op.get("resourceId"),format,size,hash);
        else {
            var saved=rows.get(0);
            if(!format.equals(saved.get("target_format")) || size!=((Number)saved.get("source_size")).longValue() || !hash.equals(saved.get("source_sha256")))
                throw ServiceException.of(IDEMPOTENCY_CONFLICT);
        }
        return view(operation,task(id));
    }
    public ModelImportStatusView get(String id) {
        return new TransactionTemplate(transactions).execute(status->view(lockOperation(id),task(id)));
    }
    public Map<String,Object> sourceTarget(String id) {
        return new TransactionTemplate(transactions).execute(status->{
            var op=lockOperation(id);var task=task(id);live(op);
            if(!"UPLOADING".equals(op.get("status")) || !"RECEIVING".equals(task.get("status"))) throw ServiceException.of(INVALID_OPERATION_STATE);
            // 原ZIP与最终闭包的filePlan分离；只签发后端确定的固定键。
            return Map.of("uploadUrl",signer.sign("resources/staging/model-imports/"+id+"/","source.zip"),
                    "method","PUT","expiresInSeconds",300,"sourceSize",task.get("source_size").toString(),"sourceSha256",task.get("source_sha256"));
        });
    }
    public ModelImportStatusView submit(String id) {
        return new TransactionTemplate(transactions).execute(status->{
            var op=lockOperation(id);var task=task(id);
            if("COMMITTED".equals(op.get("status"))) return view(op,task);
            live(op);
            if(!"UPLOADING".equals(op.get("status"))) throw ServiceException.of(INVALID_OPERATION_STATE);
            String state=(String)task.get("status");
            if("RECEIVING".equals(state)) {
                jdbc.update("UPDATE wx_3d_resource_import SET status='QUEUED',row_version=row_version+1 WHERE operation_id=?",id);
                task=task(id);
            } else if(!RUNNING.contains(state)) throw ServiceException.of(INVALID_OPERATION_STATE);
            // 只持久化待处理状态；原文件验证和转换不得占用本次请求线程或数据库锁。
            return view(op,task);
        });
    }
    public ModelImportStatusView requestPublish(String id,JsonNode body) {
        ObjectNode directory=directoryInput(body);
        return new TransactionTemplate(transactions).execute(status->{
            var op=lockOperation(id);var task=task(id);
            var previous=task.get("publish_descriptor");
            if(previous!=null && !directory.equals(publishDescriptor(previous)))
                throw ServiceException.of(IDEMPOTENCY_CONFLICT);
            if("COMMITTED".equals(op.get("status")) && previous!=null)return view(op,task);
            live(op);
            if(!"UPLOADING".equals(op.get("status")) || !"PUBLISHING".equals(task.get("status")) || cancelled(task))
                throw ServiceException.of(INVALID_OPERATION_STATE);
            if(previous==null) {
                // 目录输入与任务同锁固定，重放只查询当前状态。
                jdbc.update("UPDATE wx_3d_resource_import SET publish_descriptor=?,row_version=row_version+1 WHERE operation_id=?",
                        directory.toString(),id);
            }
            return view(op,task);
        });
    }

    private ObjectNode directoryInput(JsonNode body) {
        if(body==null || !body.isObject())bad();
        boolean hasDescription=body.has("modelDesc");
        fields(body,hasDescription?Set.of("modelName","category","modelDesc"):Set.of("modelName","category"));
        String name=text(body,"modelName",50).strip();
        if(name.length()<2)bad();
        var normalized=json.createObjectNode().put("modelName",name).put("category",text(body,"category",32).strip());
        if(hasDescription)normalized.put("modelDesc",text(body,"modelDesc",500).strip());
        return normalized;
    }

    private JsonNode publishDescriptor(Object stored) {
        try {
            String value=stored instanceof java.sql.Clob clob?clob.getSubString(1,Math.toIntExact(clob.length())):
                    stored instanceof byte[] bytes?new String(bytes,java.nio.charset.StandardCharsets.UTF_8):stored.toString();
            return json.readTree(value);
        }catch(Exception invalid){throw ServiceException.of(INVALID_OPERATION_STATE);}
    }

    private static boolean cancelled(Map<String,Object> task) {
        Object flag=task.get("cancel_requested");
        return Boolean.TRUE.equals(flag) || flag instanceof Number number && number.intValue()!=0;
    }
    private Map<String,Object> lockOperation(String id) {
        operationId(id);var actor=access.actor();
        var rows=jdbc.queryForList("SELECT * FROM wx_3d_resource_operation WHERE id=? AND operator_id=? AND ((resource_scope='GLOBAL_SYSTEM' AND tenant_id='0') OR (resource_scope='TENANT_SHARED' AND tenant_id=?)) FOR UPDATE",
                id,actor.user(),Long.toString(actor.tenant()));
        if(rows.isEmpty()) throw ServiceException.of(NOT_VISIBLE);
        var row=rows.get(0);access.maintain(actor,(String)row.get("resource_scope"),(String)row.get("tenant_id"));return row;
    }
    private Map<String,Object> task(String id) {
        var rows=jdbc.queryForList("SELECT * FROM wx_3d_resource_import WHERE operation_id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw ServiceException.of(NOT_VISIBLE);return rows.get(0);
    }
    private ModelImportStatusView view(Map<String,Object> op,Map<String,Object> task) {
        ModelImportErrorCode error=null;
        if(task.get("error_code")!=null) try { error=ModelImportErrorCode.valueOf(task.get("error_code").toString()); } catch(IllegalArgumentException ignored) { /* 不向用户回显未知内部错误。 */ }
        return ModelImportStatusView.from(op.get("id").toString(),op.get("resource_id").toString(),(String)task.get("status"),(String)op.get("status"),
                (String)task.get("target_format"),(String)task.get("attempt_id"),op.get("committed_revision")==null?null:op.get("committed_revision").toString(),error,expired(op));
    }
    private static boolean expired(Map<String,Object> op) {
        Object value=op.get("expires_at");LocalDateTime time=value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;
        return !time.isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }
    private static void live(Map<String,Object> op) { if(expired(op)) throw ServiceException.of(OPERATION_EXPIRED); }
    private static void operationId(String id) {
        if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) bad();
    }
    private static void positiveLong(String id) {try{if(!id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id)<=0) bad();}catch(NumberFormatException ex){bad();}}
    private static void fields(JsonNode node,Set<String> keys) {
        if(node==null || !node.isObject() || node.size()!=keys.size()) bad();
        for(String key:keys) if(!node.has(key)) bad();
    }
    private static String text(JsonNode node,String key,int limit) {
        JsonNode n=node==null?null:node.get(key);
        if(n==null || !n.isTextual() || n.asText().isBlank() || n.asText().length()>limit) bad();
        String text=n.asText();
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);if(c<32 || c==127) bad();
            if(Character.isHighSurrogate(c)){if(++i>=text.length() || !Character.isLowSurrogate(text.charAt(i)))bad();}
            else if(Character.isLowSurrogate(c))bad();
        }
        return text;
    }
    private static void bad() { throw new ServiceException(400,"模型导入请求格式无效"); }
}
