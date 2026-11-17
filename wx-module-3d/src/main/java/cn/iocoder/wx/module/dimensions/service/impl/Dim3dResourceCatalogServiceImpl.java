package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.dimensions.entity.Dim3dConvertedResource;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import cn.iocoder.wx.module.dimensions.entity.Dim3dResourceBaseEntity;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.Dim3dConvertedResourceMapper;
import cn.iocoder.wx.module.dimensions.mapper.Dim3dModelMapper;
import cn.iocoder.wx.module.dimensions.mapper.Dim3dTemplateMapper;
import cn.iocoder.wx.module.dimensions.service.Dim3dResourceCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 新资源目录实现，查询只返回分页所需记录，避免加载大字段。 */
@Service
public class Dim3dResourceCatalogServiceImpl implements Dim3dResourceCatalogService {

    /** 复用既有目录范围修复：公共租户系统模型共享，个人模型同时匹配租户及账号。 */
    private static final String MODEL_VISIBLE = "del_flag='0' AND ((resource_belong='SYSTEM' AND tenant_id='0')"
            + " OR (resource_belong='MINE' AND tenant_id=? AND COALESCE(data_user_id,create_by)=?))";
    private static final BeanPropertyRowMapper<Dim3dModel> MODEL_ROW = new BeanPropertyRowMapper<>(Dim3dModel.class);
    /** 请求值只映射为固定数据库列，不能把外部排序文本直接拼入查询。 */
    private static final Map<String, String> MODEL_SORT_COLUMNS = Map.of(
            "id", "id", "modelName", "model_name", "category", "category", "resourceBelong", "resource_belong",
            "status", "status", "isPaid", "is_paid", "price", "price", "priceYuan", "price",
            "createTime", "create_time", "updateTime", "update_time");
    @Resource private JdbcTemplate jdbc;
    @Resource private cn.iocoder.wx.module.dimensions.service.ArtResourceAccess access;
    /** 模型写入集中处理归属、文件关系、发布和删除，复用既有基础事务。 */
    @Resource private cn.iocoder.wx.module.dimensions.service.Dim3dModelWriteService models;

    @Resource private Dim3dModelMapper modelMapper;
    @Resource private Dim3dTemplateMapper templateMapper;
    @Resource private Dim3dConvertedResourceMapper convertedResourceMapper;
    @Resource private RestTemplate restTemplate;
    @Resource private ObjectMapper objectMapper;
    @Resource private cn.iocoder.wx.module.dimensions.service.ArtResourceService artResources;
    /** 普通图片通过独立业务服务完成目录与文件归属的共同提交。 */
    @Resource private cn.iocoder.wx.module.dimensions.service.Dim3dImageResourceService images;

    // 旧转换合同仍由部署显式配置；缺失时不能向历史测试主机转发文件和认证信息。
    @Value("${dim3d.resource-convert-url:}")
    private String convertUrl;

    @Override public Long createModel(Dim3dModel model) { return models.create(model); }
    @Override public void updateModel(Long id, Dim3dModel model) { models.update(id, model); }
    /** 目录授权后按需生成预览，避免列表逐条读取对象存储，也不让写入依赖预览服务可用性。 */
    @Override public Dim3dModel getModel(Long id) {
        return modelDetails(findModel(id));
    }

    /** 在完成目录授权的对象上补充文件投影，内部数据库关联不受响应字段影响。 */
    private Dim3dModel modelDetails(Dim3dModel model) {
        if (model.getResourceId() != null) {
            var preview = artResources.modelPreview(model.getResourceId().toString());
            model.setModelFileUrl(preview.get("modelFileUrl"));
            model.setDownloadExpiresAt(preview.get("downloadExpiresAt"));
            model.setResourceRelativePath(preview.get("resourceRelativePath"));
            model.setModelFileId(model.getResourceId());
        } else if (model.getModelFileId() != null) {
            // 历史文件编号只关联同归属租户的模型转换记录，不能用兄弟资源或任意文件地址补全。
            var paths = jdbc.queryForList("SELECT resource_relative_path FROM wx_3d_resource"
                    + " WHERE id=? AND tenant_id=? AND resource_type='MODEL' AND del_flag='0' AND resource_scope IS NULL",
                    String.class, model.getModelFileId(), model.getTenantId());
            if (paths.size() != 1 || !StringUtils.hasText(paths.get(0))) {
                throw new ServiceException(400, "模型关联资源缺少有效预览路径");
            }
            model.setResourceRelativePath(paths.get(0));
        }
        return model;
    }

    /** 同一文件可出现在多个历史目录，授权命中任一当前可见目录后仅读取一份元信息。 */
    @Override public Map<String, Object> getModelResource(Long id) {
        var actor = access.actor();
        var rows = jdbc.query("SELECT * FROM wx_3d_model WHERE (model_file_id=? OR resource_id=?) AND "
                        + MODEL_VISIBLE + " ORDER BY id LIMIT 1", MODEL_ROW,
                id, id, Long.toString(actor.tenant()), actor.user());
        if (rows.isEmpty()) throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST);
        var model = modelDetails(rows.get(0));
        var resources = jdbc.queryForList("SELECT resource_name,resource_type,asset_name FROM wx_3d_resource"
                + " WHERE id=? AND del_flag='0'", id);
        if (resources.size() != 1 || !"MODEL".equals(resources.get(0).get("resource_type"))) {
            throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST);
        }
        var resource = resources.get(0);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", id.toString());
        result.put("resourceName", resource.get("resource_name"));
        result.put("resourceType", resource.get("resource_type"));
        result.put("assetName", resource.get("asset_name"));
        result.put("resourceRelativePath", model.getResourceRelativePath());
        result.put("modelFileUrl", model.getModelFileUrl());
        result.put("downloadExpiresAt", model.getDownloadExpiresAt());
        return result;
    }

    /** 详情及写入预检共用可见范围，历史空上传账号回退创建人，未知所有者保持不可见。 */
    private Dim3dModel findModel(Long id) {
        var actor = access.actor();
        List<Dim3dModel> rows = jdbc.query("SELECT * FROM wx_3d_model WHERE id=? AND " + MODEL_VISIBLE,
                MODEL_ROW, id, Long.toString(actor.tenant()), actor.user());
        if (rows.isEmpty()) throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST);
        return rows.get(0);
    }

    @Override public PageResult<Dim3dModel> getModelPage(String name, String belong, String status, String paid, String category, Integer num, Integer size) {
        return getModelPage(name, belong, status, paid, category, num, size, null, null);
    }

    /** 计数和分页复用相同范围，单页只执行两次数据库查询，不循环签发模型地址。 */
    @Override public PageResult<Dim3dModel> getModelPage(String name, String belong, String status, String paid, String category,
                                                      Integer num, Integer size, String orderByColumn, String isAsc) {
        String order = modelOrder(orderByColumn, isAsc);
        var actor = access.actor();
        StringBuilder where = new StringBuilder(" FROM wx_3d_model WHERE ").append(MODEL_VISIBLE);
        List<Object> args = new ArrayList<>();
        args.add(Long.toString(actor.tenant()));
        args.add(actor.user());
        if (StringUtils.hasText(name)) { where.append(" AND model_name LIKE ?"); args.add("%" + name + "%"); }
        if (StringUtils.hasText(belong)) { where.append(" AND resource_belong=?"); args.add(belong); }
        if (StringUtils.hasText(status)) { where.append(" AND status=?"); args.add(status); }
        if (StringUtils.hasText(paid)) { where.append(" AND is_paid=?"); args.add(paid); }
        if (StringUtils.hasText(category)) { where.append(" AND category=?"); args.add(category); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(pageSize(size));
        args.add(((long) pageNum(num) - 1) * pageSize(size));
        List<Dim3dModel> rows = jdbc.query("SELECT *" + where + order + " LIMIT ? OFFSET ?", MODEL_ROW, args.toArray());
        // 对外文件编号映射只作用于查询结果，受管记录的历史文件列继续保持空值。
        rows.forEach(model -> { if (model.getResourceId() != null) model.setModelFileId(model.getResourceId()); });
        return new PageResult<>(rows, total == null ? 0 : total);
    }

    /** 默认按编号倒序；其他列追加编号作为同值排序键，避免跨页顺序不稳定。 */
    private static String modelOrder(String requestedColumn, String requestedDirection) {
        String column = StringUtils.hasText(requestedColumn) ? MODEL_SORT_COLUMNS.get(requestedColumn) : "id";
        String direction = StringUtils.hasText(requestedDirection) ? requestedDirection.toLowerCase(Locale.ROOT) : "desc";
        if (column == null || !List.of("asc", "desc").contains(direction)) {
            throw new ServiceException(400, "模型排序字段或方向无效");
        }
        String order = " ORDER BY " + column + " " + direction;
        return "id".equals(column) ? order : order + ", id DESC";
    }

    @Override public Long createIcon(Dim3dIcon icon) { return images.create(icon); }
    @Override public void updateIcon(Long id, Dim3dIcon icon) { legacy(getIcon(id)); images.update(id, icon); }
    @Override public Dim3dIcon getIcon(Long id) { return images.get(id); }
    @Override public PageResult<Dim3dIcon> getIconPage(String name, String category, String secondary, String belong, String status, Integer num, Integer size) {
        return images.page(name, category, secondary, belong, status, num, size);
    }

    @Override public Long createTemplate(Dim3dTemplate template) { applyTenant(template); templateMapper.insert(template); return template.getId(); }
    @Override public void updateTemplate(Long id, Dim3dTemplate template) { legacy(getTemplate(id)); applyTenant(template); template.setId(id); require(templateMapper.updateById(template)); }
    @Override public Dim3dTemplate getTemplate(Long id) { return require(templateMapper.selectById(id)); }
    @Override public PageResult<Dim3dTemplate> getTemplatePage(String name, String type, String category, String belong, String status, Integer num, Integer size) {
        Page<Dim3dTemplate> page = templateMapper.selectPage(new Page<>(pageNum(num), pageSize(size)), new LambdaQueryWrapper<Dim3dTemplate>()
                .like(StringUtils.hasText(name), Dim3dTemplate::getTemplateName, name).eq(StringUtils.hasText(type), Dim3dTemplate::getTemplateType, type)
                .eq(StringUtils.hasText(category), Dim3dTemplate::getCategory, category).eq(StringUtils.hasText(belong), Dim3dTemplate::getResourceBelong, belong)
                .eq(StringUtils.hasText(status), Dim3dTemplate::getStatus, status).orderByDesc(Dim3dTemplate::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override public void changeModelStatus(Long id, String status) { legacy(findModel(id)); checkStatus(status); Dim3dModel e = new Dim3dModel(); e.setId(id); e.setStatus(status); require(modelMapper.updateById(e)); }
    @Override public void changeIconStatus(Long id, String status) { legacy(getIcon(id)); images.changeStatus(id, status); }
    @Override public void changeTemplateStatus(Long id, String status) { legacy(getTemplate(id)); checkStatus(status); Dim3dTemplate e = new Dim3dTemplate(); e.setId(id); e.setStatus(status); require(templateMapper.updateById(e)); }
    @Override public void deleteModel(Long id) { models.delete(id); }
    @Override public void deleteIcon(Long id) { legacy(getIcon(id)); images.delete(id); }
    @Override public void deleteTemplate(Long id) { legacy(getTemplate(id)); require(templateMapper.deleteById(id)); }

    /** 将单个 ZIP 原样转发给转换服务，并仅在转换成功后落库。 */
    @Override public Dim3dConvertedResource convertAndSave(MultipartFile file, String authorization, String clientId) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || !StringUtils.hasText(originalFilename)
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw ServiceException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_ZIP);
        }
        if (!StringUtils.hasText(convertUrl)) {
            throw new ServiceException(503, "旧资源转换服务未配置");
        }
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            // 直接转发 MultipartFile 的资源句柄，避免 getBytes() 将大 ZIP 复制进 JVM 堆内存。
            body.part("file", file.getResource()).filename(originalFilename).contentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (StringUtils.hasText(authorization)) headers.set(HttpHeaders.AUTHORIZATION, authorization);
            if (StringUtils.hasText(clientId)) headers.set("ClientId", clientId);
            ResponseEntity<CommonResult<Dim3dConvertedResource>> response = restTemplate.exchange(convertUrl, HttpMethod.POST,
                    new HttpEntity<>(body.build(), headers), new ParameterizedTypeReference<>() {});
            CommonResult<Dim3dConvertedResource> result = response.getBody();
            if (result == null || !CommonResult.isSuccess(result.getCode()) || result.getData() == null) {
                throw new ServiceException(result == null ? 502 : result.getCode(), result == null ? "资源转换服务无响应" : result.getMsg());
            }
            Dim3dConvertedResource resource = result.getData();
            // 全局/受管资源即使被租户拦截隐藏，也不能由旧转换回执按同号覆盖。
            artResources.guardLegacyResource(resource.getId());
            // 转换服务返回的归属不能覆盖当前认证上下文，避免跨租户写入或误归到历史默认租户。
            var actor = access.actor();
            resource.setTenantId(Long.toString(actor.tenant()));
            // 旧转换服务同样不能指定可信上传账号，目录保存据此检查文件归属。
            resource.setDataUserId(actor.user());
            resource.setCreateBy(actor.user());
            resource.setUpdateBy(actor.user());
            if (convertedResourceMapper.selectById(resource.getId()) == null) convertedResourceMapper.insert(resource);
            else convertedResourceMapper.updateById(resource);
            return resource;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(502, "资源转换服务调用失败");
        }
    }

    /** 目录和模型文件的受管关联只能从新资源事务维护。 */
    private void legacy(Dim3dResourceBaseEntity entity) {
        artResources.guardLegacyResource(entity.getResourceId());
        if(entity instanceof Dim3dModel model) artResources.guardLegacyResource(model.getModelFileId());
    }

    /** 客户端传入的租户字段不能参与归属决策；租户过滤继续约束读取和按主键更新。 */
    private static void applyTenant(Dim3dResourceBaseEntity resource) {
        resource.setTenantId(String.valueOf(TenantContextHolder.getRequiredTenantId()));
    }

    private static int pageNum(Integer value) { return value == null || value < 1 ? 1 : value; }
    private static int pageSize(Integer value) { return value == null || value < 1 ? 10 : Math.min(value, 200); }
    private static void checkStatus(String status) { if (!Objects.equals("ONLINE", status) && !Objects.equals("OFFLINE", status)) throw new ServiceException(400, "状态只能是ONLINE或OFFLINE"); }
    private static <T> T require(T entity) { if (entity == null || (entity instanceof Integer && (Integer) entity == 0)) throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST); return entity; }
}
