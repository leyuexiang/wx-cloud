package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
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
@lombok.extern.slf4j.Slf4j
public class ModelImportService {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private ArtResourceAccess access;
    @Resource private ArtResourceService resources;
    @Resource private ArtResourceUploadSigner signer;
    @Resource private ObjectMapper json;
    @Resource private SecurityFrameworkService permissions;
    @Resource private DictDataApi dictionaries;
    @Resource private Dim3dImageResourceService images;
    @Resource private ArtResourceObjectStore objects;
    @Value("${dim3d.model-import.enabled:false}") private boolean enabled;
    private static final Set<String> RUNNING=Set.of("QUEUED","VERIFYING_SOURCE","CONVERTING","VALIDATING","PUBLISHING");
    private static final Set<String> DIRECTORY_FIELDS = Set.of("modelName", "category", "modelDesc", "coverImageId", "isPaid", "price");

    /**
     * 图片式新增入口。原包按固定缓冲计算摘要，再流式写入既有暂存键。
     * 文件处理均在数据库事务之外，转换继续由现有持久化队列承接。
     */
    public ModelImportStatusView upload(MultipartFile file, String belong) {
        requireEnabled();
        requireWritePermission("CREATE");
        if (!Set.of("SYSTEM", "MINE").contains(belong == null ? "" : belong)
                || file == null || file.isEmpty() || file.getSize() > 524288000
                || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new ServiceException(400, "请选择1字节至500MB的ZIP模型压缩包");
        }
        String hash;
        try (var input = file.getInputStream()) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            long count = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
                if (count > file.getSize()) throw new ServiceException(400, "模型文件长度不一致");
                digest.update(buffer, 0, read);
            }
            if (count != file.getSize()) throw new ServiceException(400, "模型文件长度不一致");
            hash = HexFormat.of().formatHex(digest.digest());
        } catch (ServiceException error) { throw error; }
        catch (Exception error) { throw new ServiceException(400, "模型文件读取失败，请重新选择"); }
        var body = json.createObjectNode().put("operationId", UUID.randomUUID().toString()).put("operationType", "CREATE")
                .put("resourceScope", "SYSTEM".equals(belong) ? "GLOBAL_SYSTEM" : "TENANT_SHARED").put("targetFormat", "glb");
        // 名称在保存时填写，上传阶段使用固定合法元数据，文件名不能参与路径或可信身份。
        body.putObject("metadata").put("resourceName", "待保存模型").put("assetName", "model");
        body.putObject("source").put("size", Long.toString(file.getSize())).put("sha256", hash);
        var created = create(body);
        try (var input = file.getInputStream()) {
            objects.write("resources/staging/model-imports/" + created.operationId() + "/source.zip", input, file.getSize());
        } catch (Exception error) {
            discardUpload(created.operationId());
            throw new ServiceException(503, "模型文件存储失败，请重新上传");
        }
        try { return submit(created.operationId()); }
        catch (RuntimeException error) {
            discardUpload(created.operationId());
            throw error;
        }
    }

    /** 上传尚未回执时只清理本次后台分配的原包，独立执行取消与回收，保留可审计操作。 */
    private void discardUpload(String operation) {
        try { resources.cancel(operation); }
        catch (RuntimeException error) { log.warn("模型上传失败后的操作取消待处理，操作编号 {}", operation); }
        try { objects.discardModelSource(operation); }
        catch (RuntimeException error) { log.warn("模型上传失败后的原包回收待处理，操作编号 {}", operation); }
    }

    /** 新增保存沿用原受控发布事务，同时拒绝跨资源改绑和替换操作借用新增入口。 */
    public ModelImportStatusView saveUploadedModel(JsonNode body) {
        requireEnabled();
        String id = text(body, "operationId", 36);
        String resourceId = text(body, "resourceId", 19);
        positiveLong(resourceId);
        var operation = new TransactionTemplate(transactions).execute(status -> lockOperation(id));
        if (!"CREATE".equals(operation.get("operation_type")) || !resourceId.equals(operation.get("resource_id").toString())) {
            throw ServiceException.of(IDEMPOTENCY_CONFLICT);
        }
        var directory = ((ObjectNode) body).deepCopy();
        directory.remove(List.of("operationId", "resourceId"));
        return requestPublish(id, directory);
    }

    /** 简化入口共用既有开关，未部署工作线程时及时返回可理解的服务状态。 */
    private void requireEnabled() {
        if (!enabled) throw new ServiceException(503, "模型上传服务尚未启用，请联系管理员");
    }

    public ModelImportStatusView create(JsonNode body) {
        String type=text(body,"operationType",16);
        boolean creating="CREATE".equals(type);
        if(!creating && !"REPLACE".equals(type)) bad();
        // 使用与场景列表相同的权限服务及缓存，避免编辑权限被共用入口误当作新增权限。
        requireWritePermission(type);
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
            var op=lockOperation(id);requireWritePermission((String)op.get("operation_type"));
            var task=task(id);live(op);
            if(!"UPLOADING".equals(op.get("status")) || !"RECEIVING".equals(task.get("status"))) throw ServiceException.of(INVALID_OPERATION_STATE);
            // 原ZIP与最终闭包的filePlan分离；只签发后端确定的固定键。
            return Map.of("uploadUrl",signer.sign("resources/staging/model-imports/"+id+"/","source.zip"),
                    "method","PUT","expiresInSeconds",300,"sourceSize",task.get("source_size").toString(),"sourceSha256",task.get("source_sha256"));
        });
    }
    public ModelImportStatusView submit(String id) {
        return new TransactionTemplate(transactions).execute(status->{
            var op=lockOperation(id);requireWritePermission((String)op.get("operation_type"));var task=task(id);
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
        ObjectNode directory = directoryInput(body);
        var template = new TransactionTemplate(transactions);
        var checked = template.execute(status -> checkPublication(id, directory));
        if (checked.confirmed()) return view(checked.operation(), checked.task());
        // 远程字典及文件预验均在释放操作锁后进行，已确认的重放不依赖字典服务可用性。
        validateCategory(directory.path("category").asText());
        validateDirectory(checked.operation(), directory);
        return template.execute(status -> {
            var current = checkPublication(id, directory);
            if (!current.confirmed()) {
                jdbc.update("UPDATE wx_3d_resource_import SET publish_descriptor=?,row_version=row_version+1 WHERE operation_id=?",
                        directory.toString(), id);
            }
            return view(current.operation(), current.task());
        });
    }

    private record PublicationCheck(Map<String, Object> operation, Map<String, Object> task, boolean confirmed) {}

    /** 两次短事务执行相同阶段及权限检查，避免预验期间取消、到期或并发确认被覆盖。 */
    private PublicationCheck checkPublication(String id, ObjectNode directory) {
        var op = lockOperation(id);
        requireWritePermission((String) op.get("operation_type"));
        var task = task(id);
        var previous = task.get("publish_descriptor");
        if (previous != null && !directory.equals(publishDescriptor(previous))) throw ServiceException.of(IDEMPOTENCY_CONFLICT);
        if ("COMMITTED".equals(op.get("status")) && previous != null) return new PublicationCheck(op, task, true);
        live(op);
        if (!"UPLOADING".equals(op.get("status")) || !"PUBLISHING".equals(task.get("status")) || cancelled(task)) {
            throw ServiceException.of(INVALID_OPERATION_STATE);
        }
        return new PublicationCheck(op, task, previous != null);
    }

    /** 请求确认和后台发布共用现有启用字典校验，网络调用不持有发布行锁。 */
    void validateCategory(String category) {
        if (!Boolean.TRUE.equals(dictionaries.validateDictDataList("dim3d_model_category", List.of(category)).getCheckedData())) {
            throw new ServiceException(400, "模型分类不存在或已停用");
        }
    }

    private void validateDirectory(Map<String, Object> operation, ObjectNode directory) {
        boolean system = "GLOBAL_SYSTEM".equals(operation.get("resource_scope"));
        if (!system && "PAID".equals(directory.path("isPaid").asText())) throw new ServiceException(400, "租户模型仅支持免费");
        if (directory.hasNonNull("coverImageId")) {
            long cover = directory.path("coverImageId").asLong();
            var previous = jdbc.queryForList("SELECT cover_image_id FROM wx_3d_model WHERE resource_id=? AND del_flag='0'",
                    operation.get("resource_id"));
            boolean retained = previous.size() == 1 && previous.get(0).get("cover_image_id") instanceof Number id && id.longValue() == cover;
            images.validateModelCover(cover, access.actor(), system ? "SYSTEM" : "MINE", retained);
        }
    }

    private ObjectNode directoryInput(JsonNode body) {
        if(body==null || !body.isObject())bad();
        body.fieldNames().forEachRemaining(field -> { if (!DIRECTORY_FIELDS.contains(field)) bad(); });
        String name=text(body,"modelName",50).strip();
        if(name.length()<2)bad();
        var normalized=json.createObjectNode().put("modelName",name).put("category",text(body,"category",32).strip());
        if (body.has("modelDesc")) {
            JsonNode description = body.get("modelDesc");
            if (!description.isTextual() || description.asText().length() > 500) bad();
            characters(description.asText(), true);
            normalized.put("modelDesc", description.asText().strip());
        }
        if (body.has("coverImageId")) {
            if (body.get("coverImageId").isNull()) normalized.putNull("coverImageId");
            else {
                String cover = text(body, "coverImageId", 19);
                positiveLong(cover);
                normalized.put("coverImageId", cover);
            }
        }
        if (body.has("isPaid")) {
            String paid = text(body, "isPaid", 4);
            if (!Set.of("FREE", "PAID").contains(paid)) bad();
            JsonNode price = body.get("price");
            if ("PAID".equals(paid)) {
                if (price == null || !price.isIntegralNumber() || !price.canConvertToInt() || price.asInt() <= 0) bad();
                normalized.put("price", price.asInt());
            } else {
                if (price != null && !price.isNull() && (!price.isIntegralNumber() || !price.canConvertToInt() || price.asInt() != 0)) bad();
                normalized.put("price", 0);
            }
            normalized.put("isPaid", paid);
        } else if (body.has("price")) bad();
        return normalized;
    }

    /** 请求线程只消费框架已认证身份；权限码映射与后台复核共用，不重复解释令牌。 */
    static String writePermission(String type) {
        if("CREATE".equals(type))return "dim3d:model:add";
        if("REPLACE".equals(type))return "dim3d:model:edit";
        throw ServiceException.of(INVALID_OPERATION_STATE);
    }
    private void requireWritePermission(String type) {
        if(!permissions.hasPermission(writePermission(type)))throw new ServiceException(403,"无权执行模型操作");
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
        characters(text, false);
        return text;
    }
    /** 描述允许正常换行，所有文本仍拒绝隐藏控制字符和不完整的Unicode代理项。 */
    private static void characters(String text, boolean multiline) {
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            boolean allowedWhitespace = multiline && (c == '\n' || c == '\r' || c == '\t');
            if(c<32 && !allowedWhitespace || c==127) bad();
            if(Character.isHighSurrogate(c)){if(++i>=text.length() || !Character.isLowSurrogate(text.charAt(i)))bad();}
            else if(Character.isLowSurrogate(c))bad();
        }
    }
    private static void bad() { throw new ServiceException(400,"模型导入请求格式无效"); }
}
