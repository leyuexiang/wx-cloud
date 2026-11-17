package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 文档模型表单的统一写入边界。目录编号、历史文件编号和受管资源关联在此适配。
 * 字典与权限预验在加锁前完成；文件、目录和封面归属在短事务中共同提交。
 */
@Service
public class Dim3dModelWriteService {
    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private ArtResourceAccess access;
    @Resource private SecurityFrameworkService security;
    @Resource private DictDataApi dictionaries;
    @Resource private Dim3dImageResourceService images;
    @Resource private ModelImportService imports;
    @Resource private ModelImportSourceWorker worker;
    @Resource private ArtResourceService resources;
    @Resource private ArtResourceTransactionService resourceTransactions;
    @Resource private ObjectMapper json;

    public Long create(Dim3dModel body) {
        var actor = actorWithPermission("dim3d:model:add");
        validate(body);
        requireScope(actor, body.getResourceBelong());
        var file = file(body.getModelFileId());
        requireFile(file, body, actor, false);
        if (pending(file)) return publish(body, null, file, actor);
        validateCategory(body);
        return new TransactionTemplate(transactions).execute(status -> {
            var currentFile = lockFiles(body.getModelFileId(), body.getModelFileId()).stream().findFirst()
                    .orElseThrow(() -> invalid("模型文件不存在"));
            requireFile(currentFile, body, actor, false);
            if (pending(currentFile)) throw conflict("模型文件尚未完成发布，请重试");
            if (managed(currentFile)) {
                var linked = jdbc.queryForList("SELECT * FROM wx_3d_model WHERE resource_id=? AND del_flag='0' FOR UPDATE",
                        body.getModelFileId());
                if (!linked.isEmpty()) {
                    if (linked.size() == 1 && visible(linked.get(0), actor) && sameForm(linked.get(0), body)) {
                        return ((Number) linked.get(0).get("id")).longValue();
                    }
                    throw conflict("模型文件已关联其他目录");
                }
            }
            bindFile(currentFile, body, actor, false, null);
            long id = IdWorker.getId();
            jdbc.update("""
                    INSERT INTO wx_3d_model(id,resource_id,model_file_id,model_name,model_desc,category,
                        resource_belong,is_paid,price,cover_image_id,status,tenant_id,data_user_id,create_by,update_by)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, managed(currentFile) ? body.getModelFileId() : null,
                    managed(currentFile) ? null : body.getModelFileId(), body.getModelName(), body.getModelDesc(),
                    body.getCategory(), body.getResourceBelong(), body.getIsPaid(), body.getPrice(), body.getCoverImageId(),
                    body.getStatus(), owner(actor, body.getResourceBelong()), actor.user(), actor.user(), actor.user());
            return id;
        });
    }

    public void update(Long id, Dim3dModel body) {
        var actor = actorWithPermission("dim3d:model:edit");
        validate(body);
        var previous = directory(id, actor, false, false);
        requireScope(actor, previous.get("resource_belong").toString());
        requireScope(actor, body.getResourceBelong());
        var file = file(body.getModelFileId());
        requireFile(file, body, actor, Objects.equals(fileId(previous), body.getModelFileId()));
        if (pending(file)) {
            publish(body, id, file, actor);
            return;
        }
        validateCategory(body);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // 同时锁定旧、新资源，并按主键排序，避免两个目录反向换文件时形成锁顺序环。
            var files = lockFiles(fileId(previous), body.getModelFileId());
            var current = directory(id, actor, true, false);
            requireSameBinding(previous, current);
            // 在目录行锁内保全省略字段，避免锁等待期间覆盖另一笔刚提交的描述或封面。
            if (!body.isModelDescSpecified()) body.setModelDesc((String) current.get("model_desc"));
            if (!body.isCoverImageSpecified()) body.setCoverImageId(number(current.get("cover_image_id")));
            var selected = files.stream().filter(row -> Objects.equals(row.get("id"), body.getModelFileId()))
                    .findFirst().orElseThrow(() -> invalid("模型文件不存在"));
            requireFile(selected, body, actor, Objects.equals(fileId(current), body.getModelFileId()));
            if (pending(selected)) throw conflict("模型文件尚未完成发布，请重试");
            if (managed(selected) && !jdbc.queryForList("SELECT id FROM wx_3d_model WHERE resource_id=?"
                    + " AND del_flag='0' AND id<>? LIMIT 1 FOR UPDATE", body.getModelFileId(), id).isEmpty()) {
                throw conflict("模型文件已关联其他目录");
            }
            boolean retainedCover = Objects.equals(number(current.get("cover_image_id")), body.getCoverImageId())
                    && Objects.equals(current.get("resource_belong"), body.getResourceBelong());
            bindFile(selected, body, actor, retainedCover, id);
            // 只改目录关联与元数据，旧资源及已发布引用保留，可选字段使用已授权的原值或本次显式值。
            jdbc.update("""
                    UPDATE wx_3d_model SET resource_id=?,model_file_id=?,model_name=?,model_desc=?,category=?,
                        resource_belong=?,is_paid=?,price=?,cover_image_id=?,tenant_id=?,data_user_id=?,
                        update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?
                    """, managed(selected) ? body.getModelFileId() : null, managed(selected) ? null : body.getModelFileId(),
                    body.getModelName(), body.getModelDesc(), body.getCategory(), body.getResourceBelong(), body.getIsPaid(),
                    body.getPrice(), body.getCoverImageId(), owner(actor, body.getResourceBelong()), actor.user(), actor.user(), id);
        });
    }

    /**
     * 上下线仅更新目录状态，历史文件及受管文件共用同一权限和归属边界。
     * 远程维护权限在锁外预检，事务内重新锁定有效目录并核对归属，避免覆盖并发迁移。
     * 重复提交同一状态直接成功，不刷新审计时间，也不触发文件转换、签名或资源修订。
     */
    public void changeStatus(Long id, String state) {
        var actor = actorWithPermission("dim3d:model:edit");
        if (!"ONLINE".equals(state) && !"OFFLINE".equals(state)) throw invalid("模型状态仅支持 ONLINE 或 OFFLINE");
        var previous = directory(id, actor, false, false);
        requireScope(actor, previous.get("resource_belong").toString());
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            var current = directory(id, actor, true, false);
            if (!Objects.equals(previous.get("tenant_id"), current.get("tenant_id"))
                    || !Objects.equals(previous.get("resource_belong"), current.get("resource_belong"))) {
                throw conflict("模型归属已变化，请刷新后重试");
            }
            if (state.equals(current.get("status"))) return;
            // 身份取自认证账号，只修改状态及更新审计，保全文件关联、业务元数据和已发布引用。
            jdbc.update("UPDATE wx_3d_model SET status=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    state, actor.user(), id);
        });
    }

    /** 受管删除在同一数据库事务中复用原资源 DELETE 提交，引用拒绝时目录保持原样。 */
    public void delete(Long id) {
        var actor = actorWithPermission("dim3d:model:delete");
        var previous = directory(id, actor, false, true);
        requireScope(actor, previous.get("resource_belong").toString());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            lockFiles(fileId(previous), fileId(previous));
            var current = directory(id, actor, true, true);
            requireSameBinding(previous, current);
            if ("1".equals(current.get("del_flag"))) return;
            if (current.get("resource_id") != null) {
                var resource = file(fileId(current));
                ObjectNode request = json.createObjectNode().put("operationId", UUID.randomUUID().toString())
                        .put("operationType", "DELETE").put("resourceId", current.get("resource_id").toString())
                        .put("expectedRevision", resource.get("current_revision").toString());
                var operation = resources.create(request);
                var result = resourceTransactions.commit(operation.get("operationId").toString(), json.createObjectNode());
                if (!"COMMITTED".equals(result.get("status"))) throw conflict("模型删除尚未提交，请重试");
                jdbc.update("UPDATE wx_3d_model SET update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?", actor.user(), id);
            } else {
                jdbc.update("UPDATE wx_3d_model SET del_flag='1',status='OFFLINE',update_by=?,"
                        + "update_time=CURRENT_TIMESTAMP WHERE id=?", actor.user(), id);
            }
        });
    }

    /** 定向发布只处理已转换的当前文件，复用原租约及最终提交，不在接口内等待或重复转换。 */
    private Long publish(Dim3dModel body, Long target, Map<String, Object> file, ArtResourceAccess.Actor actor) {
        var operations = jdbc.queryForList("""
                SELECT o.id FROM wx_3d_resource_operation o JOIN wx_3d_resource_import i ON i.operation_id=o.id
                WHERE o.resource_id=? AND o.operator_id=? AND o.operation_type='CREATE' AND o.status='UPLOADING'
                    AND i.status='PUBLISHING' AND i.cancel_requested=FALSE ORDER BY o.id LIMIT 2
                """, String.class, body.getModelFileId(), actor.user());
        if (operations.size() != 1 || !"WEB_UPLOAD".equals(file.get("upload_source"))) {
            throw conflict("模型文件尚未转换完成或上传操作不可用");
        }
        String operation = operations.get(0);
        ObjectNode directory = json.createObjectNode().put("modelName", body.getModelName())
                .put("category", body.getCategory())
                .put("isPaid", body.getIsPaid()).put("price", body.getPrice());
        // 目标目录的省略字段由最终发布事务取锁定现值，显式空值才固定清空意图。
        if (target == null || body.isModelDescSpecified()) directory.put("modelDesc", Objects.toString(body.getModelDesc(), ""));
        if (target == null || body.isCoverImageSpecified()) {
            if (body.getCoverImageId() == null) directory.putNull("coverImageId");
            else directory.put("coverImageId", body.getCoverImageId().toString());
        }
        if (target == null) imports.requestPublish(operation, directory);
        else imports.requestPublishForDirectory(operation, directory, target);
        worker.publishOperation(operation);
        var state = imports.get(operation);
        if (!"COMMITTED".equals(state.operationStatus())) {
            if (state.errorNumber() != null) throw new ServiceException(state.errorNumber(), state.errorMessage());
            throw new ServiceException(503, "模型保存正在处理，请使用相同参数重试");
        }
        var ids = jdbc.queryForList("SELECT id FROM wx_3d_model WHERE resource_id=? AND del_flag='0'", Long.class,
                body.getModelFileId());
        if (ids.size() != 1 || target != null && !target.equals(ids.get(0))) {
            throw conflict("模型发布结果与目录关联不一致");
        }
        return ids.get(0);
    }

    /** 服务层仍验证表单，内部调用不能绕开控制器必填及金额规则。 */
    private void validate(Dim3dModel body) {
        if (body == null || !StringUtils.hasText(body.getModelName()) || !StringUtils.hasText(body.getCategory())
                || !Set.of("SYSTEM", "MINE").contains(Objects.toString(body.getResourceBelong(), ""))
                || !Set.of("FREE", "PAID").contains(Objects.toString(body.getIsPaid(), ""))
                || body.getModelFileId() == null || body.getModelFileId() <= 0) throw invalid("模型必填字段无效");
        body.setModelName(body.getModelName().strip());
        body.setCategory(body.getCategory().strip());
        if (body.getModelName().length() < 2 || body.getModelName().length() > 50
                || body.getCategory().length() > 32 || body.getModelDesc() != null && body.getModelDesc().length() > 500
                || body.getCoverImageId() != null && body.getCoverImageId() <= 0) throw invalid("模型字段长度或封面编号无效");
        if ("PAID".equals(body.getIsPaid())) {
            if (body.getPrice() == null || body.getPrice() <= 0) throw invalid("付费模型价格必须是正整数分");
        } else {
            if (body.getPrice() != null && body.getPrice() != 0) throw invalid("免费模型价格必须为零");
            body.setPrice(0);
        }
    }

    /** 已转换上传的字典校验由原发布服务执行，普通目录写入在加锁前校验一次。 */
    private void validateCategory(Dim3dModel body) {
        if (!Boolean.TRUE.equals(dictionaries.validateDictDataList("dim3d_model_category", List.of(body.getCategory()))
                .getCheckedData())) throw invalid("模型分类不存在或已停用");
    }

    private void bindFile(Map<String, Object> file, Dim3dModel body, ArtResourceAccess.Actor actor,
                          boolean retainedCover, Long directoryId) {
        if (!managed(file)) {
            // 已被其他目录复用的历史文件不能原地改变租户，避免破坏它们的可见范围和预览。
            if (!owner(actor, body.getResourceBelong()).equals(Objects.toString(file.get("tenant_id"), ""))
                    && !jdbc.queryForList("SELECT id FROM wx_3d_model WHERE model_file_id=? AND del_flag='0'"
                    + " AND id<>? LIMIT 1 FOR UPDATE", body.getModelFileId(), directoryId == null ? 0L : directoryId).isEmpty()) {
                throw conflict("模型文件仍被其他目录使用，修改归属请重新上传");
            }
            jdbc.update("UPDATE wx_3d_resource SET tenant_id=?,update_by=? WHERE id=?",
                    owner(actor, body.getResourceBelong()), actor.user(), body.getModelFileId());
        }
        if (body.getCoverImageId() != null) {
            images.bindModelCover(body.getCoverImageId(), actor, body.getResourceBelong(), retainedCover);
        }
    }

    private Map<String, Object> file(Long id) {
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_resource WHERE id=?", id);
        if (rows.size() != 1) throw invalid("模型文件不存在");
        return rows.get(0);
    }

    /** 两个占位参数固定且有序加锁，文件编号不会参与 SQL 文本拼接。 */
    private List<Map<String, Object>> lockFiles(Long first, Long second) {
        return jdbc.queryForList("SELECT * FROM wx_3d_resource WHERE id IN (?,?) ORDER BY id FOR UPDATE", first, second);
    }

    private void requireFile(Map<String, Object> file, Dim3dModel body, ArtResourceAccess.Actor actor, boolean retained) {
        if (!"MODEL".equals(file.get("resource_type")) || !"0".equals(file.get("del_flag"))) throw invalid("模型文件不可用");
        String tenant = Objects.toString(file.get("tenant_id"), "");
        if (managed(file)) {
            String scope = "SYSTEM".equals(body.getResourceBelong()) ? "GLOBAL_SYSTEM" : "TENANT_SHARED";
            if (!scope.equals(file.get("resource_scope")) || !owner(actor, body.getResourceBelong()).equals(tenant)) {
                throw new ServiceException(403, "模型文件与所选归属不一致");
            }
            if (!Set.of("READY", "PENDING").contains(Objects.toString(file.get("content_state"), ""))) {
                throw invalid("模型文件不可用");
            }
        } else if (!StringUtils.hasText(Objects.toString(file.get("resource_relative_path"), ""))) {
            throw invalid("模型文件缺少预览路径");
        }
        boolean personalOwner = Long.toString(actor.user()).equals(Objects.toString(
                file.get("data_user_id") == null ? file.get("create_by") : file.get("data_user_id"), ""));
        boolean globalFile = "SYSTEM".equals(body.getResourceBelong()) && "0".equals(tenant);
        if (!(globalFile || Long.toString(actor.tenant()).equals(tenant) && (personalOwner || retained))) {
            throw new ServiceException(403, "无权关联此模型文件");
        }
    }

    private Map<String, Object> directory(Long id, ArtResourceAccess.Actor actor, boolean lock, boolean deleted) {
        if (id == null || id <= 0) throw invalid("模型编号无效");
        var rows = jdbc.queryForList("SELECT * FROM wx_3d_model WHERE id=?" + (deleted ? "" : " AND del_flag='0'")
                + (lock ? " FOR UPDATE" : ""), id);
        if (rows.size() != 1 || !visible(rows.get(0), actor)) throw new ServiceException(404, "模型不存在或无权访问");
        return rows.get(0);
    }

    /** 同目录读取一致的可信范围，供原导入服务和最终发布事务复用。 */
    static boolean visible(Map<String, Object> row, ArtResourceAccess.Actor actor) {
        String tenant = Objects.toString(row.get("tenant_id"), "");
        Object user = row.get("data_user_id") == null ? row.get("create_by") : row.get("data_user_id");
        return "SYSTEM".equals(row.get("resource_belong")) && "0".equals(tenant)
                || "MINE".equals(row.get("resource_belong")) && Long.toString(actor.tenant()).equals(tenant)
                && Long.toString(actor.user()).equals(Objects.toString(user, ""));
    }

    private ArtResourceAccess.Actor actorWithPermission(String permission) {
        var actor = access.actor();
        if (!security.hasPermission(permission)) throw new ServiceException(403, "无权执行模型操作");
        return actor;
    }

    private void requireScope(ArtResourceAccess.Actor actor, String belong) {
        access.maintain(actor, "SYSTEM".equals(belong) ? "GLOBAL_SYSTEM" : "TENANT_SHARED", owner(actor, belong));
    }

    private static String owner(ArtResourceAccess.Actor actor, String belong) {
        return "SYSTEM".equals(belong) ? "0" : Long.toString(actor.tenant());
    }

    private static boolean managed(Map<String, Object> file) { return file.get("resource_scope") != null; }
    private static boolean pending(Map<String, Object> file) { return managed(file) && "PENDING".equals(file.get("content_state")); }
    private static Long number(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private static Long fileId(Map<String, Object> row) { return number(row.get("resource_id") == null ? row.get("model_file_id") : row.get("resource_id")); }

    /** 锁等待期间发生文件或归属修改时要求重新提交，避免覆盖刚完成的文件替换。 */
    private static void requireSameBinding(Map<String, Object> previous, Map<String, Object> current) {
        for (String field : List.of("resource_id", "model_file_id", "resource_belong", "tenant_id")) {
            if (!Objects.equals(previous.get(field), current.get(field))) throw conflict("模型文件或归属已变化，请刷新后重试");
        }
    }

    private static boolean sameForm(Map<String, Object> row, Dim3dModel body) {
        return Objects.equals(row.get("model_name"), body.getModelName())
                && Objects.equals(Objects.toString(row.get("model_desc"), ""), Objects.toString(body.getModelDesc(), ""))
                && Objects.equals(row.get("category"), body.getCategory())
                && Objects.equals(row.get("resource_belong"), body.getResourceBelong())
                && Objects.equals(row.get("is_paid"), body.getIsPaid())
                && Objects.equals(Objects.toString(row.get("price"), "0"), body.getPrice().toString())
                && Objects.equals(number(row.get("cover_image_id")), body.getCoverImageId());
    }

    private static ServiceException invalid(String message) { return new ServiceException(400, message); }
    private static ServiceException conflict(String message) { return new ServiceException(409, message); }
}
