package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.dimensions.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** 原 ZIP、转换、产物校验与受控发布执行器。 */
@Service
public class ModelImportSourceWorker implements AutoCloseable {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private ArtResourceObjectStore store;
    @Resource private ArtResourceAccess access;
    @Resource private ModelImportConversionClient converter;
    @Resource private ModelImportArtifactValidation artifactValidation;
    @Resource private ModelImportPublication publication;
    @Resource private ArtResourceTransactionService resourceTransactions;
    private final ObjectMapper json=new ObjectMapper();
    private final Semaphore slots=new Semaphore(1);
    private final ScheduledThreadPoolExecutor heartbeat=new ScheduledThreadPoolExecutor(1,r->{
        Thread thread=new Thread(r,"model-import-lease");thread.setDaemon(true);return thread;
    });
    public ModelImportSourceWorker(){heartbeat.setRemoveOnCancelPolicy(true);}
    public record Lease(String operationId,String attemptId,String resourceId,long sourceSize,String sourceSha256,String frozenSourceKey) {}

    /** 所有租约和截止判断来自同一数据库UTC时钟，避免工作进程时钟偏差。 */
    protected LocalDateTime databaseNow() {
        return jdbc.queryForObject("SELECT UTC_TIMESTAMP(3)",Timestamp.class).toLocalDateTime();
    }
    private <T>T tx(Supplier<T> action){return new TransactionTemplate(transactions).execute(status->action.get());}

    private enum ClaimStage { SOURCE, CONVERSION, VALIDATION, PUBLICATION }

    public Optional<Lease> claimNext() {return claimNext(ClaimStage.SOURCE);}

    private Optional<Lease> claimNext(ClaimStage stage) {
        LocalDateTime now=databaseNow();
        // 有界候选，随后统一按操作→任务锁顺序重新判断，避免与提交端倒序死锁。
        String selection=switch(stage) {
            case SOURCE -> "((status='QUEUED' AND available_at<=?) OR (status='VERIFYING_SOURCE' AND lease_until<=?))";
            case CONVERSION -> "status='CONVERTING' AND lease_until<=? AND artifact_descriptor IS NULL";
            case VALIDATION -> "status='VALIDATING' AND lease_until<=?";
            case PUBLICATION -> "status='PUBLISHING' AND lease_until<=? AND publish_descriptor IS NOT NULL";
        };
        String order=stage==ClaimStage.SOURCE?"available_at":"lease_until";
        var candidates=jdbc.queryForList("SELECT operation_id FROM wx_dim3d_resource_import WHERE cancel_requested=FALSE AND "+
                selection+" ORDER BY "+order+",operation_id LIMIT 16",String.class,
                stage==ClaimStage.SOURCE?new Object[]{now,now}:new Object[]{now});
        for(String id:candidates) {
            var lease=tx(()->claim(id,stage));if(lease!=null)return Optional.of(lease);
        }
        return Optional.empty();
    }

    private Lease claim(String id,ClaimStage stage) {
        var rows=lock(id);if(rows==null)return null;
        var op=rows.get(0);var task=rows.get(1);LocalDateTime now=databaseNow();
        String state=(String)task.get("status");
        boolean leaseExpired=task.get("lease_until")!=null && !time(task.get("lease_until")).isAfter(now);
        boolean ready=switch(stage) {
            case SOURCE -> ("QUEUED".equals(state) && !time(task.get("available_at")).isAfter(now)) ||
                    ("VERIFYING_SOURCE".equals(state) && leaseExpired);
            case CONVERSION -> "CONVERTING".equals(state) && leaseExpired && task.get("artifact_descriptor")==null;
            case VALIDATION -> "VALIDATING".equals(state) && leaseExpired;
            case PUBLICATION -> "PUBLISHING".equals(state) && leaseExpired && task.get("publish_descriptor")!=null;
        };
        if(cancelled(task) || !ready)return null;
        if(!eligible(op,task,now)) {
            // 无效候选不能长期占据首批位置；成功状态只同步原操作回执。
            String terminal=Set.of("COMMITTED","CANCELLED","EXPIRED","CONFLICT").contains(op.get("status"))?(String)op.get("status"):
                    !time(op.get("expires_at")).isAfter(now)?"EXPIRED":"FAILED";
            jdbc.update("UPDATE wx_dim3d_resource_import SET status=?,error_code=?,lease_until=NULL,row_version=row_version+1 WHERE operation_id=?",terminal,
                    "FAILED".equals(terminal)?ModelImportErrorCode.INVALID_MODEL.name():null,id);
            return null;
        }
        try { maintain(op); } catch(ServiceException denied) {
            jdbc.update("UPDATE wx_dim3d_resource_import SET status='FAILED',error_code=?,lease_until=NULL,row_version=row_version+1 WHERE operation_id=?",classify(denied).name(),id);return null;
        }
        now=databaseNow();
        if(!time(op.get("expires_at")).isAfter(now))return null;
        String attempt=UUID.randomUUID().toString();
        jdbc.update("UPDATE wx_dim3d_resource_import SET status=?,attempt_id=?,lease_until=?,error_code=NULL,row_version=row_version+1 WHERE operation_id=?",
                stage==ClaimStage.SOURCE?"VERIFYING_SOURCE":state,attempt,now.plusSeconds(120),id);
        return new Lease(id,attempt,op.get("resource_id").toString(),((Number)task.get("source_size")).longValue(),(String)task.get("source_sha256"),(String)task.get("frozen_source_key"));
    }
    public Optional<Lease> prepareNext() {
        if(!slots.tryAcquire())return Optional.empty();
        try {return prepareClaimed();}
        finally {slots.release();}
    }

    private Optional<Lease> prepareClaimed() {
        var selected=claimNext();if(selected.isEmpty())return selected;var lease=selected.get();
        var keepAlive=keepAlive(lease,"VERIFYING_SOURCE",new AtomicBoolean(true));
        try {
            if(!freezeSource(lease))return Optional.empty();
            // 续租期间拿不到新领取资格；若进程在阶段切换后退出，超时后由新尝试接手。
            var keys=jdbc.queryForList("SELECT frozen_source_key FROM wx_dim3d_resource_import WHERE operation_id=? AND attempt_id=? AND status='CONVERTING'",
                    String.class,lease.operationId(),lease.attemptId());
            if(keys.isEmpty())return Optional.empty();
            return Optional.of(new Lease(lease.operationId(),lease.attemptId(),lease.resourceId(),lease.sourceSize(),lease.sourceSha256(),keys.get(0)));
        } finally {keepAlive.cancel(false);}
    }

    /** 优先处理已固定产物；即使转换服务暂停，也能继续验证已有 ZIP。 */
    public boolean runNext() {
        if(!slots.tryAcquire())return false;
        try {
            if(publication!=null && publication.configured()) {
                var pending=claimNext(ClaimStage.PUBLICATION);
                if(pending.isPresent())return publishClaimed(pending.get());
            }
            var validation=claimNext(ClaimStage.VALIDATION);
            if(validation.isPresent())return validateClaimed(validation.get());
            if(!converter.configured())return false;
            var selected=claimNext(ClaimStage.CONVERSION).or(this::prepareClaimed);
            return selected.isPresent() && convertClaimed(selected.get());
        } finally {slots.release();}
    }

    private ScheduledFuture<?> keepAlive(Lease lease,String phase,AtomicBoolean active) {
        return heartbeat.scheduleWithFixedDelay(()->{
            try {if(!renew(lease,phase))active.set(false);}
            catch(RuntimeException ignored){ /* 暂时无法续租不代表仍有所有权，产物固定时再次验租。 */ }
        },20,20,TimeUnit.SECONDS);
    }

    public boolean renew(Lease lease) {return renew(lease,"VERIFYING_SOURCE");}

    private boolean renew(Lease lease,String phase) {
        return tx(()->{
            var rows=current(lease,phase);if(rows==null)return false;
            maintain(rows.get(0));
            var now=databaseNow();if(!liveLease(rows,now))return false;
            jdbc.update("UPDATE wx_dim3d_resource_import SET lease_until=? WHERE operation_id=?",now.plusSeconds(120),lease.operationId());return true;
        });
    }

    private boolean convertClaimed(Lease lease) {
        String phase="CONVERTING";var active=new AtomicBoolean(true);
        var keepAlive=keepAlive(lease,phase,active);
        try {
            String format=tx(()->{
                var rows=current(lease,phase);if(rows==null)return null;
                maintain(rows.get(0));
                return (String)rows.get(1).get("target_format");
            });
            if(format==null)return false;
            String sourceKey=lease.frozenSourceKey();
            String operationPrefix="resources/model-imports/"+lease.operationId()+"/";
            if(sourceKey==null || !sourceKey.startsWith(operationPrefix) ||
                    !sourceKey.substring(operationPrefix.length()).matches("[A-Za-z0-9-]+/source\\.zip"))
                throw new ModelImportConversionClient.Failure(ModelImportErrorCode.UNSUPPORTED_INPUT);
            try(var archive=fetchArchive(lease,format,active)) {
                // 转换返回后先检查租约和权限；上传期间失去资格时最多留下独立孤立对象。
                if(!active.get() || !renew(lease,phase))return false;
                String key=ArtResourceObjectStore.objectKey(operationPrefix+lease.attemptId()+"/","converted.zip");
                storeArchive(key,archive);
                String descriptor=json.createObjectNode().put("key",key).put("size",Long.toString(archive.size()))
                        .put("sha256",archive.sha256()).put("format",format).toString();
                return tx(()->{
                    var rows=current(lease,phase);if(rows==null)return false;
                    maintain(rows.get(0));
                    // 权限调用也可能跨过租约截止；回写前使用同一数据库时钟再次确认。
                    var now=databaseNow();if(!liveLease(rows,now))return false;
                    jdbc.update("UPDATE wx_dim3d_resource_import SET status='VALIDATING',artifact_descriptor=?,lease_until=?,row_version=row_version+1 WHERE operation_id=?",
                            descriptor,now,lease.operationId());
                    return true;
                });
            }
        } catch(ModelImportConversionClient.Failure failure) {
            recordFailure(lease,phase,failure.error());return false;
        } catch(ServiceException denied) {
            if(classify(denied)!=ModelImportErrorCode.IMPORT_PERMISSION_REVOKED)throw denied;
            recordFailure(lease,phase,ModelImportErrorCode.IMPORT_PERMISSION_REVOKED);return false;
        } finally {keepAlive.cancel(false);}
    }

    private record ValidationInput(String format,ObjectNode descriptor) {}

    private boolean validateClaimed(Lease lease) {
        String phase="VALIDATING";
        var active=new AtomicBoolean(true);
        var keepAlive=keepAlive(lease,phase,active);
        try {
            var input=tx(()->{
                var rows=current(lease,phase);
                if(rows==null)return null;
                var task=rows.get(1);
                return new ValidationInput((String)task.get("target_format"),readArtifact(task.get("artifact_descriptor")));
            });
            if(input==null)return false;
            var checked=artifactValidation.validate(lease.operationId(),input.format(),input.descriptor(),active::get);
            var result=input.descriptor().deepCopy();
            var validated=result.putObject("validated");
            validated.put("modelPath",checked.modelPath());
            var files=validated.putArray("files");
            for(var file:checked.files()) {
                files.addObject().put("path",file.path()).put("size",Long.toString(file.size())).put("sha256",file.sha256());
            }
            return tx(()->{
                var rows=current(lease,phase);
                if(rows==null)return false;
                maintain(rows.get(0));
                var now=databaseNow();
                // 固定 ZIP 身份及当前租约都必须与校验开始时一致。
                if(!liveLease(rows,now) || !input.descriptor().equals(readArtifact(rows.get(1).get("artifact_descriptor"))))return false;
                jdbc.update("UPDATE wx_dim3d_resource_import SET status='PUBLISHING',artifact_descriptor=?,lease_until=?,row_version=row_version+1 WHERE operation_id=?",
                        result.toString(),now,lease.operationId());
                return true;
            });
        } catch(ModelImportArtifactValidation.Failure failure) {
            recordFailure(lease,phase,failure.code());
            return false;
        } catch(CancellationException stopped) {
            return false;
        } catch(ServiceException denied) {
            if(classify(denied)!=ModelImportErrorCode.IMPORT_PERMISSION_REVOKED)throw denied;
            recordFailure(lease,phase,ModelImportErrorCode.IMPORT_PERMISSION_REVOKED);
            return false;
        } finally {keepAlive.cancel(false);}
    }

    private ObjectNode readArtifact(Object raw) {
        try {
            var descriptor=json.readTree(jsonText(raw));
            if(descriptor instanceof ObjectNode object && object.size()==4)return object;
        } catch(Exception invalid){ /* 固定身份损坏时不能重新转换覆盖。 */ }
        throw new ModelImportArtifactValidation.Failure(ModelImportErrorCode.ARTIFACT_UNAVAILABLE);
    }

    private record PublicationInput(String format,String prefix,ObjectNode artifact,ObjectNode directory,String assetName) {}

    private boolean publishClaimed(Lease lease) {
        String phase="PUBLISHING";
        var active=new AtomicBoolean(true);
        var keepAlive=keepAlive(lease,phase,active);
        try {
            var input=tx(()->{
                var rows=current(lease,phase);
                if(rows==null)return null;
                var op=rows.get(0);var task=rows.get(1);
                String assetName=jdbc.queryForObject("SELECT asset_name FROM wx_dim3d_resource WHERE id=?",String.class,lease.resourceId());
                String prefix=op.get("storage_prefix")+"frozen/"+lease.attemptId()+"/";
                return new PublicationInput((String)task.get("target_format"),prefix,
                        readObject(task.get("artifact_descriptor")),readObject(task.get("publish_descriptor")),assetName);
            });
            if(input==null)return false;
            var ready=publication.freeze(lease.operationId(),lease.resourceId(),input.format(),input.assetName(),
                    input.prefix(),input.artifact(),active::get);
            return tx(()->{
                // 资源行先加锁，与既有 ART 最终事务的顺序一致。
                var resources=jdbc.queryForList("SELECT * FROM wx_dim3d_resource WHERE id=? FOR UPDATE",lease.resourceId());
                if(resources.isEmpty())return false;
                var rows=current(lease,phase);
                if(rows==null)return false;
                var op=rows.get(0);var task=rows.get(1);
                if(!input.artifact().equals(readObject(task.get("artifact_descriptor"))) ||
                        !input.directory().equals(readObject(task.get("publish_descriptor"))))return false;
                maintain(op);
                if(!liveLease(rows,databaseNow()))return false;
                boolean committed=resourceTransactions.commitModel(resources.get(0),op,ready,input.directory());
                jdbc.update("UPDATE wx_dim3d_resource_import SET status=?,lease_until=NULL,error_code=NULL,row_version=row_version+1 WHERE operation_id=?",
                        committed?"COMMITTED":"CONFLICT",lease.operationId());
                // 若目录锁等待跨过截止，回滚本事务的全部写入。
                if(!liveLease(rows,databaseNow()))throw new CancellationException();
                return committed;
            });
        } catch(ModelImportArtifactValidation.Failure failure) {
            // 临时存储失败保留固定计划，租约过期后可重新准备同一批内容。
            if(failure.code()!=ModelImportErrorCode.ARTIFACT_STORAGE_UNAVAILABLE)
                recordFailure(lease,phase,failure.code());
            return false;
        } catch(CancellationException stopped) {
            return false;
        } catch(ServiceException rejected) {
            var error=classify(rejected);
            recordFailure(lease,phase,error==ModelImportErrorCode.IMPORT_PERMISSION_REVOKED?error:ModelImportErrorCode.INVALID_MODEL);
            return false;
        } finally {keepAlive.cancel(false);}
    }

    private ObjectNode readObject(Object raw) {
        try {
            if(json.readTree(jsonText(raw)) instanceof ObjectNode object)return object;
        } catch(Exception invalid){ /* 固定输入损坏时停止发布。 */ }
        throw new ModelImportArtifactValidation.Failure(ModelImportErrorCode.ARTIFACT_UNAVAILABLE);
    }

    private ModelImportConversionClient.Archive fetchArchive(Lease lease,String format,AtomicBoolean active) {
        ModelImportConversionClient.Archive archive=null;
        try(InputStream source=store.open(lease.frozenSourceKey())) {
            archive=converter.convert(source,lease.sourceSize(),format,active::get);
        } catch(ModelImportConversionClient.Failure failure) {throw failure;}
        catch(Exception unavailable) {
            if(archive!=null)archive.close();
            throw new ModelImportConversionClient.Failure(ModelImportErrorCode.SOURCE_STORAGE_UNAVAILABLE);
        }
        return archive;
    }

    private void storeArchive(String key,ModelImportConversionClient.Archive archive) {
        try {
            // 尝试编号只由服务端生成；遇到已有对象只复核，绝不覆盖该尝试的字节。
            if(!store.exists(key)) {
                try(InputStream input=Files.newInputStream(archive.file())) {store.write(key,input,archive.size());}
            }
            store.verifyObject(key,new ArtResourceManifestVerifier.FileSpec("converted.zip",archive.size(),archive.sha256()));
        } catch(Exception unavailable) {throw new ModelImportConversionClient.Failure(ModelImportErrorCode.ARTIFACT_STORAGE_UNAVAILABLE);}
    }

    public boolean freezeSource(Lease lease) {
        try {
            if(!renew(lease))return false;
            String destination=lease.frozenSourceKey();
            var file=new ArtResourceManifestVerifier.FileSpec("source.zip",lease.sourceSize(),lease.sourceSha256());
            if(destination==null) {
                String prefix="resources/model-imports/"+lease.operationId()+"/"+lease.attemptId()+"/";
                // IO不持有数据库锁，源在两次读取间被改写也须由冻结后的摘要检查发现。
                store.freeze("resources/staging/model-imports/"+lease.operationId()+"/",prefix,List.of(file));
                destination=ArtResourceObjectStore.objectKey(prefix,"source.zip");
            } else {
                if(!destination.startsWith("resources/model-imports/"+lease.operationId()+"/") || !destination.endsWith("/source.zip"))
                    throw ServiceException.of(ArtResourceErrorCode.UNSAFE_PATH);
                int boundary=destination.lastIndexOf('/');
                ArtResourceObjectStore.objectKey(destination.substring(0,boundary+1),"source.zip");
                store.verifyObject(destination,file);
            }
            int slash=destination.lastIndexOf('/');
            store.verifyZipHeader(destination.substring(0,slash+1),destination.substring(slash+1));
            String key=destination;
            return tx(()->{
                var rows=current(lease,"VERIFYING_SOURCE");if(rows==null)return false;maintain(rows.get(0));
                var now=databaseNow();if(!liveLease(rows,now))return false;
                jdbc.update("UPDATE wx_dim3d_resource_import SET status='CONVERTING',frozen_source_key=?,lease_until=?,row_version=row_version+1 WHERE operation_id=?",key,now.plusSeconds(120),lease.operationId());return true;
            });
        } catch(RuntimeException error) {
            recordFailure(lease,"VERIFYING_SOURCE",classify(error));return false;
        }
    }
    private void recordFailure(Lease lease,String phase,ModelImportErrorCode error) {
        // 数据库不可用或本尝试失租约时不写错误覆盖新尝试，保留可恢复状态。
        tx(()->{
            if(current(lease,phase)!=null)jdbc.update("UPDATE wx_dim3d_resource_import SET status='FAILED',error_code=?,lease_until=NULL,row_version=row_version+1 WHERE operation_id=?",error.name(),lease.operationId());
            return null;
        });
    }
    private List<Map<String,Object>> current(Lease lease,String phase) {
        var rows=lock(lease.operationId());if(rows==null)return null;
        var task=rows.get(1);LocalDateTime now=databaseNow();
        if(!phase.equals(task.get("status")) || !lease.attemptId().equals(task.get("attempt_id")) ||
                !liveLease(rows,now) ||
                cancelled(task) || !eligible(rows.get(0),task,now) || "CONVERTING".equals(phase) &&
                (task.get("artifact_descriptor")!=null || !Objects.equals(lease.frozenSourceKey(),task.get("frozen_source_key"))))return null;
        return rows;
    }
    private static boolean liveLease(List<Map<String,Object>> rows,LocalDateTime now) {
        Object leaseUntil=rows.get(1).get("lease_until");
        return leaseUntil!=null && time(leaseUntil).isAfter(now) && time(rows.get(0).get("expires_at")).isAfter(now);
    }
    private List<Map<String,Object>> lock(String id) {
        var op=jdbc.queryForList("SELECT * FROM wx_dim3d_resource_operation WHERE id=? FOR UPDATE",id);
        if(op.isEmpty())return null;
        var task=jdbc.queryForList("SELECT * FROM wx_dim3d_resource_import WHERE operation_id=? FOR UPDATE",id);
        return task.isEmpty()?null:List.of(op.get(0),task.get(0));
    }
    private boolean eligible(Map<String,Object> op,Map<String,Object> task,LocalDateTime now) {
        if(!"UPLOADING".equals(op.get("status")) || !time(op.get("expires_at")).isAfter(now) ||
                !Objects.equals(op.get("resource_id"),task.get("resource_id")))return false;
        try {
            if(!json.readTree(jsonText(op.get("candidate_descriptor"))).path("modelImport").asBoolean(false))return false;
        }catch(Exception invalid){return false;}
        return jdbc.queryForObject("SELECT COUNT(*) FROM wx_dim3d_resource WHERE id=? AND resource_scope=? AND tenant_id=? AND upload_source='WEB_UPLOAD' AND del_flag='0'",Long.class,
                op.get("resource_id"),op.get("resource_scope"),op.get("tenant_id"))==1;
    }
    private static String jsonText(Object raw) throws java.sql.SQLException {
        if(raw instanceof java.sql.Clob clob)return clob.getSubString(1,Math.toIntExact(clob.length()));
        if(raw instanceof byte[] bytes)return new String(bytes,StandardCharsets.UTF_8);
        return String.valueOf(raw);
    }
    private void maintain(Map<String,Object> op) {
        Long previous=TenantContextHolder.getTenantId();boolean previousIgnore=TenantContextHolder.isIgnore();
        try {
            // GLOBAL_SYSTEM的所有者0不能作为认证租户，平台真实租户由受信服务配置提供。
            long tenant="GLOBAL_SYSTEM".equals(op.get("resource_scope"))?platformTenant:Long.parseLong(op.get("tenant_id").toString());
            TenantContextHolder.setTenantId(tenant);
            TenantContextHolder.setIgnore(false);
            access.maintain(new ArtResourceAccess.Actor(((Number)op.get("operator_id")).longValue(),tenant),(String)op.get("resource_scope"),(String)op.get("tenant_id"));
        } finally {
            TenantContextHolder.clear();
            if(previous!=null)TenantContextHolder.setTenantId(previous);
            if(previousIgnore)TenantContextHolder.setIgnore(true);
        }
    }
    @org.springframework.beans.factory.annotation.Value("${dim3d.resource.platform-tenant-id:0}") private long platformTenant;
    private static ModelImportErrorCode classify(RuntimeException error) {
        if(error instanceof ServiceException service) {
            int code=service.getCode();
            if(code==ArtResourceErrorCode.CHECKSUM_MISMATCH.getCode())return ModelImportErrorCode.SOURCE_CHECKSUM_MISMATCH;
            if(code==ArtResourceErrorCode.OBJECT_MISSING.getCode())return ModelImportErrorCode.SOURCE_MISSING;
            if(code==ArtResourceErrorCode.INVALID_CLOSURE.getCode() || code==ArtResourceErrorCode.UNSAFE_PATH.getCode())return ModelImportErrorCode.UNSUPPORTED_INPUT;
            if(code==403 || code==ArtResourceErrorCode.ADMIN_REQUIRED.getCode())return ModelImportErrorCode.IMPORT_PERMISSION_REVOKED;
        }
        return ModelImportErrorCode.SOURCE_STORAGE_UNAVAILABLE;
    }
    private static LocalDateTime time(Object value){return value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;}
    private static boolean cancelled(Map<String,Object> task) {
        Object flag=task.get("cancel_requested");return Boolean.TRUE.equals(flag) || flag instanceof Number number&&number.intValue()!=0;
    }
    @PreDestroy @Override public void close(){heartbeat.shutdownNow();}
}
