package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.entity.SceneDef;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public interface SceneDefService extends IService<SceneDef> {
    CommonResult<?> uploadScene(MultipartFile jsonFile,String name ,String photoAddress) throws IOException;

    void downLoadScene(int[] ids, HttpServletResponse response) throws UnsupportedEncodingException;

    Boolean judgeSceneName(Integer id, String sceneName);

}
