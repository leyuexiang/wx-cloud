package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 普通图片目录及文件绑定。现阶段两表使用同一数据库，在短事务内共同提交可见范围。
 * 文件内容及签名仍由基础设施服务负责，本服务仅维护图片业务与文件归属关系。
 */
@Service
public class Dim3dImageResourceService {
    private static final String VISIBLE = "del_flag='0' AND ((resource_belong='SYSTEM' AND tenant_id='0'%s)"
            + " OR (resource_belong='MINE' AND tenant_id=?))";
    private static final String PLATFORM_VISIBLE = VISIBLE.formatted("");
    private static final String TENANT_VISIBLE = VISIBLE.formatted(" AND status='ONLINE'");
    private static final BeanPropertyRowMapper<Dim3dIcon> ROW = new BeanPropertyRowMapper<>(Dim3dIcon.class);
    /** 仅允许按图标目录的公开业务列排序。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id", "iconName", "icon_name", "category", "category", "secondaryCategory", "secondary_category",
            "resourceBelong", "resource_belong", "status", "status", "createTime", "create_time", "updateTime", "update_time");

    @Resource private JdbcTemplate jdbc;
    @Resource private PlatformTransactionManager transactions;
    @Resource private ArtResourceAccess access;
    @Resource private DictDataApi dictionaries;
    @Resource private SecurityFrameworkService security;

    /** 字典远程校验先完成，随后锁定文件并同时提交目录与归属，避免网络请求占用数据库锁。 */
    public Long create(Dim3dIcon body) {
        var actor = access.actor();
        requirePermission("dim3d:icon:add");
        validate(body);
        validateDescription(body.getIconDesc(), null);
        requireScope(actor, body.getResourceBelong());
        long id = IdWorker.getId();
        return new TransactionTemplate(transactions).execute(status -> {
            bindFile(body.getIconFileId(), actor, body.getResourceBelong(), false);
            jdbc.update("""
                    INSERT INTO wx_3d_icon
                    (id,icon_name,icon_desc,category,secondary_category,resource_belong,icon_file_id,status,
                     data_user_id,tenant_id,create_by,update_by,create_time,update_time,del_flag)
                    VALUES(?,?,?,?,?,?,?,'ONLINE',?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'0')
                    """, id, body.getIconName(), body.getIconDesc(), body.getCategory(), body.getSecondaryCategory(),
                    body.getResourceBelong(), body.getIconFileId(), actor.user(), owner(actor, body.getResourceBelong()),
                    actor.user(), actor.user());
            return id;
        });
    }

    /** 未替换文件时保留原绑定。新文件绑定和目录更新共享事务，异常时恢复原关联。 */
    public void update(Long id, Dim3dIcon body) {
        var actor = access.actor();
        requirePermission("dim3d:icon:edit");
        validate(body);
        requireScope(actor, body.getResourceBelong());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Dim3dIcon current = lockWritable(id, actor);
            validateDescription(body.getIconDesc(), current.getIconDesc());
            boolean retainedFile = Objects.equals(current.getIconFileId(), body.getIconFileId());
            bindFile(body.getIconFileId(), actor, body.getResourceBelong(), retainedFile);
            jdbc.update("""
                    UPDATE wx_3d_icon SET icon_name=?,icon_desc=?,category=?,secondary_category=?,
                    resource_belong=?,icon_file_id=?,tenant_id=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?
                    """, body.getIconName(), body.getIconDesc(), body.getCategory(), body.getSecondaryCategory(),
                    body.getResourceBelong(), body.getIconFileId(), owner(actor, body.getResourceBelong()), actor.user(), id);
        });
    }

    public Dim3dIcon get(Long id) {
        return find(id, access.actor(), false);
    }

    /** 计数和分页共享归属及状态条件；租户只能看到已上线的系统目录。 */
    public PageResult<Dim3dIcon> page(String name, String category, String secondary, String belong,
                                     String status, Integer number, Integer size, String orderByColumn, String isAsc) {
        var actor = access.actor();
        String order = Dim3dCatalogOrder.sql(SORT_COLUMNS, orderByColumn, isAsc, "图标排序字段或方向无效");
        int page = number == null || number < 1 ? 1 : number;
        int limit = size == null || size < 1 ? 10 : Math.min(size, 200);
        long offset = ((long) page - 1) * limit;
        // 已限定上线或本租户资源时，现有筛选已满足范围要求，跳过运营角色查询。
        String visibility = "ONLINE".equals(status) || "MINE".equals(belong)
                ? PLATFORM_VISIBLE : readVisibility(actor);
        StringBuilder where = new StringBuilder(" FROM wx_3d_icon WHERE ").append(visibility);
        List<Object> args = new ArrayList<>();
        args.add(Long.toString(actor.tenant()));
        if (StringUtils.hasText(name)) { where.append(" AND icon_name LIKE ?"); args.add("%" + name.trim() + "%"); }
        if (StringUtils.hasText(category)) { where.append(" AND category=?"); args.add(category); }
        if (StringUtils.hasText(secondary)) { where.append(" AND secondary_category=?"); args.add(secondary); }
        if (StringUtils.hasText(belong)) {
            if (!Set.of("SYSTEM", "MINE").contains(belong)) throw invalid("资源归属无效");
            where.append(" AND resource_belong=?"); args.add(belong);
        }
        if (StringUtils.hasText(status)) {
            requireStatus(status);
            where.append(" AND status=?"); args.add(status);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        // 空页直接返回总数，避免额外查询及操作人批量调用。
        if (offset >= total) return new PageResult<>(List.of(), total);
        args.add(limit);
        args.add(offset);
        List<Dim3dIcon> rows = jdbc.query("SELECT *" + where + order + " LIMIT ? OFFSET ?", ROW, args.toArray());
        return new PageResult<>(rows, total);
    }

    /** 同一目录的维护操作串行化，重复状态在权限校验后返回并保留原审计。 */
    public void changeStatus(Long id, String state) {
        var actor = access.actor();
        requirePermission("dim3d:icon:edit");
        requireStatus(state);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Dim3dIcon current = lockWritable(id, actor);
            if (state.equals(current.getStatus())) return;
            jdbc.update("UPDATE wx_3d_icon SET status=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    state, actor.user(), id);
        });
    }

    /** 删除只变更目录审计状态，文件仍可被其他目录或已保存场景引用。 */
    public void delete(Long id) {
        var actor = access.actor();
        requirePermission("dim3d:icon:delete");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            lockWritable(id, actor);
            jdbc.update("UPDATE wx_3d_icon SET del_flag='1',update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                    actor.user(), id);
        });
    }

    private Dim3dIcon find(Long id, ArtResourceAccess.Actor actor, boolean lock) {
        if (id == null || id <= 0) throw invalid("图片编号无效");
        // 锁定维护仍需找到已下线记录，写权限由锁内校验；普通详情沿用分页可见规则。
        List<Dim3dIcon> rows = jdbc.query("SELECT * FROM wx_3d_icon WHERE id=? AND "
                + (lock ? PLATFORM_VISIBLE : readVisibility(actor))
                + (lock ? " FOR UPDATE" : ""), ROW, id, Long.toString(actor.tenant()));
        if (rows.isEmpty()) throw new ServiceException(404, "图片不存在或无权访问");
        return rows.get(0);
    }

    /** 运营维护权限允许查看系统下线记录，租户图片的读取仍只比较租户。 */
    private String readVisibility(ArtResourceAccess.Actor actor) {
        return access.isPlatformAdministrator(actor) ? PLATFORM_VISIBLE : TENANT_VISIBLE;
    }

    /** 锁定有效记录后统一校验受管关联和维护权限，供编辑、状态及删除共用。 */
    private Dim3dIcon lockWritable(Long id, ArtResourceAccess.Actor actor) {
        Dim3dIcon current = find(id, actor, true);
        ArtResourceService.guardLegacyResource(jdbc, current.getResourceId());
        requireWrite(actor, current);
        return current;
    }

    private void validate(Dim3dIcon body) {
        if (body == null || !StringUtils.hasText(body.getIconName())) throw invalid("图片名称不能为空");
        body.setIconName(body.getIconName().trim());
        if (body.getIconName().length() > 50) throw invalid("图片名称最多 50 个字符");
        if (body.getIconFileId() == null || body.getIconFileId() <= 0) throw invalid("请先上传图片");
        if (!Set.of("SYSTEM", "MINE").contains(Objects.toString(body.getResourceBelong(), ""))) throw invalid("资源归属无效");
        if (!StringUtils.hasText(body.getCategory()) || !Boolean.TRUE.equals(dictionaries
                .validateDictDataList("dim3d_icon_category", List.of(body.getCategory())).getCheckedData())) {
            throw invalid("图片分类不存在或已停用");
        }
    }

    /** 编辑用已锁定记录比对，历史长描述未改变时仍可保存其他业务字段。 */
    private static void validateDescription(String description, String previous) {
        if (description != null && description.length() > 200 && !description.equals(previous)) {
            throw invalid("图片描述最多 200 个字符");
        }
    }

    private void bindFile(long id, ArtResourceAccess.Actor actor, String belong, boolean retainedReference) {
        fileForBinding(id, actor, belong, retainedReference, true);
        updateFileBinding(id, actor, belong);
    }

    /** 模型确认前仅校验文件，不提前改变私有上传的可见范围。 */
    void validateModelCover(long id, ArtResourceAccess.Actor actor, String belong, boolean retainedReference) {
        requireModelCover(fileForBinding(id, actor, belong, retainedReference, false));
    }

    /** 模型发布事务持有目录锁后再锁封面，文件归属随目录、修订和回执共同提交。 */
    void bindModelCover(long id, ArtResourceAccess.Actor actor, String belong, boolean retainedReference) {
        requireModelCover(fileForBinding(id, actor, belong, retainedReference, true));
        updateFileBinding(id, actor, belong);
    }

    /** 原封面随目录迁移前核对旧范围。拒绝改动其他模型、图标及模板仍在使用的封面。 */
    void bindModelCover(long id, ArtResourceAccess.Actor actor, String belong, String previousBelong, Long directoryId) {
        if (previousBelong == null || previousBelong.equals(belong)) {
            bindModelCover(id, actor, belong, previousBelong != null);
            return;
        }
        requireModelCover(fileForBinding(id, actor, previousBelong, true, true));
        String previousTenant = owner(actor, previousBelong);
        // 使用当前锁定读，避免发布事务在等待封面行锁前建立的旧快照漏掉并发提交的关联。
        // 模型和图标的文件归属与目录租户一致。文件型模板可引用公共封面，需跨租户检查。
        boolean shared = !jdbc.queryForList("SELECT id FROM wx_3d_model WHERE tenant_id=? AND cover_image_id=?"
                        + " AND id<>? AND del_flag='0' LIMIT 1 FOR UPDATE", previousTenant, id, directoryId).isEmpty()
                || !jdbc.queryForList("SELECT id FROM wx_3d_icon WHERE tenant_id=? AND icon_file_id=?"
                        + " AND del_flag='0' LIMIT 1 FOR UPDATE", previousTenant, id).isEmpty()
                || !jdbc.queryForList("SELECT id FROM wx_3d_template WHERE cover_image_id=?"
                        + " AND del_flag='0' LIMIT 1 FOR UPDATE", id).isEmpty();
        if (shared) {
            throw new ServiceException(409, "模型封面仍被其他目录使用，请更换或移除封面");
        }
        updateFileBinding(id, actor, belong);
    }

    /** 图片目录和模型封面共用上传者、租户及保留引用判断，各读取只访问一条文件记录。 */
    private Map<String, Object> fileForBinding(long id, ArtResourceAccess.Actor actor, String belong,
                                             boolean retainedReference, boolean lock) {
        var rows = jdbc.queryForList("SELECT tenant_id,uploader_id,visibility_scope,type,size FROM wx_infra_file WHERE id=? AND deleted=0"
                + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw invalid("图片文件不存在或尚未上传完成");
        Map<String, Object> file = rows.get(0);
        String scope = "SYSTEM".equals(belong) ? "GLOBAL" : "TENANT";
        String targetTenant = owner(actor, belong);
        boolean sameScope = scope.equals(file.get("visibility_scope"))
                && targetTenant.equals(Objects.toString(file.get("tenant_id"), ""));
        boolean ownedUpload = Long.toString(actor.user()).equals(Objects.toString(file.get("uploader_id"), ""))
                && Long.toString(actor.tenant()).equals(Objects.toString(file.get("tenant_id"), ""))
                && "PRIVATE_UPLOAD".equals(file.get("visibility_scope"));
        boolean ownBoundFile = sameScope && Long.toString(actor.user())
                .equals(Objects.toString(file.get("uploader_id"), ""));
        if (!(ownedUpload || ownBoundFile || retainedReference && sameScope)) throw new ServiceException(403, "无权绑定此图片文件，修改归属时请重新上传");
        if (!Objects.toString(file.get("type"), "").startsWith("image/")
                || !(file.get("size") instanceof Number bytes) || bytes.longValue() < 1) throw invalid("文件不是有效图片");
        return file;
    }

    /** 封面格式和大小与现有模型弹窗一致，不能仅依赖浏览器限制。 */
    private static void requireModelCover(Map<String, Object> file) {
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(Objects.toString(file.get("type"), ""))
                || ((Number) file.get("size")).longValue() > 5L * 1024 * 1024) {
            throw invalid("模型封面须为不超过5MiB的PNG、JPEG或WebP图片");
        }
    }

    private void updateFileBinding(long id, ArtResourceAccess.Actor actor, String belong) {
        String scope = "SYSTEM".equals(belong) ? "GLOBAL" : "TENANT";
        jdbc.update("UPDATE wx_infra_file SET visibility_scope=?,tenant_id=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                scope, Long.parseLong(owner(actor, belong)), id);
    }

    private void requireWrite(ArtResourceAccess.Actor actor, Dim3dIcon current) {
        if ("SYSTEM".equals(current.getResourceBelong())) {
            access.maintain(actor, "GLOBAL_SYSTEM", "0");
        } else if (!Objects.equals(current.getCreateBy(), actor.user())) {
            access.maintain(actor, "TENANT_SHARED", Long.toString(actor.tenant()));
        }
    }

    private void requireScope(ArtResourceAccess.Actor actor, String scope) {
        if ("SYSTEM".equals(scope)) access.maintain(actor, "GLOBAL_SYSTEM", "0");
    }

    private void requirePermission(String permission) {
        if (!security.hasPermission(permission)) throw new ServiceException(403, "无权执行图片操作");
    }

    private static String owner(ArtResourceAccess.Actor actor, String scope) {
        return "SYSTEM".equals(scope) ? "0" : Long.toString(actor.tenant());
    }

    private static void requireStatus(String status) {
        if (!"ONLINE".equals(status) && !"OFFLINE".equals(status)) throw invalid("图片状态无效");
    }

    private static ServiceException invalid(String message) { return new ServiceException(400, message); }
}
