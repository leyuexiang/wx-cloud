package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.Dim3dTemplateMapper;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 文件型模板目录按认证租户查询，写入保留原模板文件身份和受管资源边界。 */
@Service
public class Dim3dFileTemplateService {
    /** 文件型及合并目录共用业务列白名单。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "id", "id", "templateName", "template_name", "templateType", "template_type", "category", "category",
            "resourceBelong", "resource_belong", "status", "status", "createTime", "create_time", "updateTime", "update_time");
    private static final BeanPropertyRowMapper<Dim3dTemplate> ROW = new BeanPropertyRowMapper<>(Dim3dTemplate.class);

    @Resource private JdbcTemplate jdbc;
    @Resource private Dim3dTemplateMapper templateMapper;
    @Resource private ArtResourceAccess access;
    @Resource private PlatformTransactionManager transactions;

    /** 沿用文件型目录自增编号，新增状态及审计字段只取服务端可信值。 */
    public Long create(Dim3dTemplate template) {
        var actor = access.actor();
        normalizeWriteType(template);
        template.setTenantId(Long.toString(actor.tenant()));
        template.setCreateBy(actor.user());
        template.setUpdateBy(actor.user());
        var now = LocalDateTime.now();
        template.setCreateTime(now);
        template.setUpdateTime(now);
        template.setDelFlag("0");
        template.setStatus("ONLINE");
        templateMapper.insert(template);
        return template.getId();
    }

    /** 锁定目录并检查现有受管关联。保留原状态及未提交的可选字段。 */
    public void update(Long id, Dim3dTemplate template) {
        var actor = access.actor();
        normalizeWriteType(template);
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            lockWritable(id, actor);
            jdbc.update("""
                    UPDATE wx_3d_template SET template_name=?,template_desc=COALESCE(?,template_desc),template_type=?,
                    category=?,resource_belong=?,cover_image_id=COALESCE(?,cover_image_id),template_file_id=?,
                    update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND del_flag='0'
                    """, template.getTemplateName(), template.getTemplateDesc(), template.getTemplateType(), template.getCategory(),
                    template.getResourceBelong(), template.getCoverImageId(), template.getTemplateFileId(),
                    actor.user(), id, Long.toString(actor.tenant()));
        });
    }

    public Dim3dTemplate get(Long id) {
        return find(id, access.actor(), false);
    }

    /** 计数和分页共用租户、逻辑删除及业务筛选，空页跳过目录读取。 */
    public PageResult<Dim3dTemplate> page(String name, String type, String category, String belong, String status,
                                         Integer number, Integer size, String orderByColumn, String direction) {
        var actor = access.actor();
        int limit = size == null || size < 1 ? 10 : Math.min(size, 200);
        long offset = number == null || number < 1 ? 0 : (number - 1L) * limit;
        String order = orderBy(orderByColumn, direction);
        StringBuilder where = new StringBuilder(" FROM wx_3d_template WHERE tenant_id=? AND del_flag='0'");
        List<Object> args = new ArrayList<>();
        args.add(Long.toString(actor.tenant()));
        if (StringUtils.hasText(name)) { where.append(" AND template_name LIKE ?"); args.add("%" + name + "%"); }
        if (StringUtils.hasText(type)) { where.append(" AND template_type=?"); args.add(Dim3dSceneService.normalizeTemplateType(type)); }
        if (StringUtils.hasText(category)) { where.append(" AND category=?"); args.add(category); }
        if (StringUtils.hasText(belong)) { where.append(" AND resource_belong=?"); args.add(belong); }
        if (StringUtils.hasText(status)) { where.append(" AND status=?"); args.add(status); }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        if (offset >= total) return new PageResult<>(List.of(), total);
        args.add(limit);
        args.add(offset);
        var rows = jdbc.query("SELECT *" + where + order + " LIMIT ? OFFSET ?", ROW, args.toArray());
        return new PageResult<>(rows, total);
    }

    /** 重复状态请求仍检查目录权限，但保留既有审计。 */
    public void changeStatus(Long id, String status) {
        if (!"ONLINE".equals(status) && !"OFFLINE".equals(status)) {
            throw new ServiceException(400, "模板状态无效");
        }
        var actor = access.actor();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            if (status.equals(lockWritable(id, actor).getStatus())) return;
            jdbc.update("UPDATE wx_3d_template SET status=?,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND del_flag='0'",
                    status, actor.user(), id, Long.toString(actor.tenant()));
        });
    }

    /** 删除目录采用逻辑删除，场景或其他目录引用的模板文件仍由原链路维护。 */
    public void delete(Long id) {
        var actor = access.actor();
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            lockWritable(id, actor);
            jdbc.update("UPDATE wx_3d_template SET del_flag='1',update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=? AND tenant_id=? AND del_flag='0'",
                    actor.user(), id, Long.toString(actor.tenant()));
        });
    }

    private Dim3dTemplate lockWritable(Long id, ArtResourceAccess.Actor actor) {
        var template = find(id, actor, true);
        ArtResourceService.guardLegacyResource(jdbc, template.getResourceId());
        return template;
    }

    private Dim3dTemplate find(Long id, ArtResourceAccess.Actor actor, boolean lock) {
        if (id == null || id <= 0) throw new ServiceException(400, "模板编号无效");
        var rows = jdbc.query("SELECT * FROM wx_3d_template WHERE id=? AND tenant_id=? AND del_flag='0'"
                + (lock ? " FOR UPDATE" : ""), ROW, id, Long.toString(actor.tenant()));
        if (rows.isEmpty()) throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST);
        return rows.get(0);
    }

    static String orderBy(String column, String direction) {
        return Dim3dCatalogOrder.sql(SORT_COLUMNS, column, direction, "模板排序字段或方向无效");
    }

    /** 持久化采用契约枚举，兼容现有网页提交的字典类型编码。 */
    private static void normalizeWriteType(Dim3dTemplate template) {
        String type = Dim3dSceneService.normalizeTemplateType(template.getTemplateType());
        if (!"INDUSTRY".equals(type) && !"EXHIBITION".equals(type)) throw new ServiceException(400, "模板类型无效");
        template.setTemplateType(type);
    }
}
