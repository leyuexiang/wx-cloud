package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.framework.common.exception.ServiceException;
import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dConvertedResource;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dModel;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dTemplate;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.Dim3dConvertedResourceMapper;
import cn.iocoder.jtyf.module.dimensions.mapper.Dim3dIconMapper;
import cn.iocoder.jtyf.module.dimensions.mapper.Dim3dModelMapper;
import cn.iocoder.jtyf.module.dimensions.mapper.Dim3dTemplateMapper;
import cn.iocoder.jtyf.module.dimensions.service.Dim3dResourceCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Objects;

/** 新资源目录实现，查询只返回分页所需记录，避免加载大字段。 */
@Service
public class Dim3dResourceCatalogServiceImpl implements Dim3dResourceCatalogService {

    @Resource private Dim3dModelMapper modelMapper;
    @Resource private Dim3dIconMapper iconMapper;
    @Resource private Dim3dTemplateMapper templateMapper;
    @Resource private Dim3dConvertedResourceMapper convertedResourceMapper;
    @Resource private RestTemplate restTemplate;
    @Resource private ObjectMapper objectMapper;

    @Value("${dim3d.resource-convert-url:http://192.168.10.200:30080/api/dim3d/resource/uploadConvert}")
    private String convertUrl;

    @Override public Long createModel(Dim3dModel model) { modelMapper.insert(model); return model.getId(); }
    @Override public void updateModel(Long id, Dim3dModel model) { model.setId(id); require(modelMapper.updateById(model)); }
    @Override public Dim3dModel getModel(Long id) { return require(modelMapper.selectById(id)); }
    @Override public PageResult<Dim3dModel> getModelPage(String name, String belong, String status, String paid, String category, Integer num, Integer size) {
        Page<Dim3dModel> page = modelMapper.selectPage(new Page<>(pageNum(num), pageSize(size)), new LambdaQueryWrapper<Dim3dModel>()
                .like(StringUtils.hasText(name), Dim3dModel::getModelName, name).eq(StringUtils.hasText(belong), Dim3dModel::getResourceBelong, belong)
                .eq(StringUtils.hasText(status), Dim3dModel::getStatus, status).eq(StringUtils.hasText(paid), Dim3dModel::getIsPaid, paid)
                .eq(StringUtils.hasText(category), Dim3dModel::getCategory, category).orderByDesc(Dim3dModel::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override public Long createIcon(Dim3dIcon icon) { iconMapper.insert(icon); return icon.getId(); }
    @Override public void updateIcon(Long id, Dim3dIcon icon) { icon.setId(id); require(iconMapper.updateById(icon)); }
    @Override public Dim3dIcon getIcon(Long id) { return require(iconMapper.selectById(id)); }
    @Override public PageResult<Dim3dIcon> getIconPage(String name, String category, String secondary, String belong, String status, Integer num, Integer size) {
        Page<Dim3dIcon> page = iconMapper.selectPage(new Page<>(pageNum(num), pageSize(size)), new LambdaQueryWrapper<Dim3dIcon>()
                .like(StringUtils.hasText(name), Dim3dIcon::getIconName, name).eq(StringUtils.hasText(category), Dim3dIcon::getCategory, category)
                .eq(StringUtils.hasText(secondary), Dim3dIcon::getSecondaryCategory, secondary).eq(StringUtils.hasText(belong), Dim3dIcon::getResourceBelong, belong)
                .eq(StringUtils.hasText(status), Dim3dIcon::getStatus, status).orderByDesc(Dim3dIcon::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override public Long createTemplate(Dim3dTemplate template) { templateMapper.insert(template); return template.getId(); }
    @Override public void updateTemplate(Long id, Dim3dTemplate template) { template.setId(id); require(templateMapper.updateById(template)); }
    @Override public Dim3dTemplate getTemplate(Long id) { return require(templateMapper.selectById(id)); }
    @Override public PageResult<Dim3dTemplate> getTemplatePage(String name, String type, String category, String belong, String status, Integer num, Integer size) {
        Page<Dim3dTemplate> page = templateMapper.selectPage(new Page<>(pageNum(num), pageSize(size)), new LambdaQueryWrapper<Dim3dTemplate>()
                .like(StringUtils.hasText(name), Dim3dTemplate::getTemplateName, name).eq(StringUtils.hasText(type), Dim3dTemplate::getTemplateType, type)
                .eq(StringUtils.hasText(category), Dim3dTemplate::getCategory, category).eq(StringUtils.hasText(belong), Dim3dTemplate::getResourceBelong, belong)
                .eq(StringUtils.hasText(status), Dim3dTemplate::getStatus, status).orderByDesc(Dim3dTemplate::getId));
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    @Override public void changeModelStatus(Long id, String status) { checkStatus(status); Dim3dModel e = new Dim3dModel(); e.setId(id); e.setStatus(status); require(modelMapper.updateById(e)); }
    @Override public void changeIconStatus(Long id, String status) { checkStatus(status); Dim3dIcon e = new Dim3dIcon(); e.setId(id); e.setStatus(status); require(iconMapper.updateById(e)); }
    @Override public void changeTemplateStatus(Long id, String status) { checkStatus(status); Dim3dTemplate e = new Dim3dTemplate(); e.setId(id); e.setStatus(status); require(templateMapper.updateById(e)); }
    @Override public void deleteModel(Long id) { require(modelMapper.deleteById(id)); }
    @Override public void deleteIcon(Long id) { require(iconMapper.deleteById(id)); }
    @Override public void deleteTemplate(Long id) { require(templateMapper.deleteById(id)); }

    /** 将单个 ZIP 原样转发给转换服务，并仅在转换成功后落库。 */
    @Override public Dim3dConvertedResource convertAndSave(MultipartFile file, String authorization, String clientId) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || !StringUtils.hasText(originalFilename)
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw ServiceException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_ZIP);
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
            resource.setTenantId(resource.getTenantId() == null ? "000000" : resource.getTenantId());
            if (convertedResourceMapper.selectById(resource.getId()) == null) convertedResourceMapper.insert(resource);
            else convertedResourceMapper.updateById(resource);
            return resource;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException(502, "资源转换服务调用失败");
        }
    }

    private static int pageNum(Integer value) { return value == null || value < 1 ? 1 : value; }
    private static int pageSize(Integer value) { return value == null || value < 1 ? 10 : Math.min(value, 200); }
    private static void checkStatus(String status) { if (!Objects.equals("ONLINE", status) && !Objects.equals("OFFLINE", status)) throw new ServiceException(400, "状态只能是ONLINE或OFFLINE"); }
    private static <T> T require(T entity) { if (entity == null || (entity instanceof Integer && (Integer) entity == 0)) throw ServiceException.of(ErrorCodeConstants.DATA_NOT_EXIST); return entity; }
}
