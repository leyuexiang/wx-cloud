package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.framework.common.pojo.PageResult;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dConvertedResource;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dModel;
import cn.iocoder.jtyf.module.dimensions.entity.Dim3dTemplate;
import org.springframework.web.multipart.MultipartFile;

/** 新资源目录及转换服务的业务边界。 */
public interface Dim3dResourceCatalogService {
    Long createModel(Dim3dModel model);
    void updateModel(Long id, Dim3dModel model);
    Dim3dModel getModel(Long id);
    PageResult<Dim3dModel> getModelPage(String modelName, String resourceBelong, String status, String isPaid, String category, Integer pageNum, Integer pageSize);
    Long createIcon(Dim3dIcon icon);
    void updateIcon(Long id, Dim3dIcon icon);
    Dim3dIcon getIcon(Long id);
    PageResult<Dim3dIcon> getIconPage(String iconName, String category, String secondaryCategory, String resourceBelong, String status, Integer pageNum, Integer pageSize);
    Long createTemplate(Dim3dTemplate template);
    void updateTemplate(Long id, Dim3dTemplate template);
    Dim3dTemplate getTemplate(Long id);
    PageResult<Dim3dTemplate> getTemplatePage(String templateName, String templateType, String category, String resourceBelong, String status, Integer pageNum, Integer pageSize);
    void changeModelStatus(Long id, String status);
    void changeIconStatus(Long id, String status);
    void changeTemplateStatus(Long id, String status);
    void deleteModel(Long id);
    void deleteIcon(Long id);
    void deleteTemplate(Long id);
    Dim3dConvertedResource convertAndSave(MultipartFile file, String authorization, String clientId);
}
