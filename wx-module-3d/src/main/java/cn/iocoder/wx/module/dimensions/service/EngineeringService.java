package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.vo.*;
import cn.iocoder.wx.module.dimensions.entity.Engineering;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface EngineeringService extends IService<Engineering> {

    Integer create(Engineering name);

    List<ProjectEngineeringVO> find(LambdaQueryWrapper<Engineering> lambdaQueryWrapper, boolean findOne);

    void setCollection(ProjectEngineeringCollectionVO projectEngineeringCollectionVo);

    void setPublish(ProjectEngineeringPublishVO projectEngineeringPublishVo);

    void change(ProjectEngineeringCreateVO projectEngineeringCreateVo);

    Integer setName(Integer id, String name);

    List<ProjectEngineeringVO> getSceneList(String platformType,String sort, Integer id, String project, String name, String nodes, String comsInstance, String type, Integer isPublish, String findType);

    Engineering copyScene(String id);

    int uploadOrUpdateScene(String platformType, MultipartFile jsonFile, Integer id,String name,String photoAddress) throws IOException;

    void downLoadScene(int[] ids, HttpServletResponse response);

    Boolean judgeSceneName(String platformType, Integer id, String sceneName);

    Boolean updateWebSceneInfo(ProjectEngineeringUpdateVO projectEngineeringUpdateVO);

    List<IsPublishVO> getIsPublishList();
}
