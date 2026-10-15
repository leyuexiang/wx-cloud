package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.dto.MaterialReturnDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.MaterialPageVo;
import cn.iocoder.wx.module.dimensions.domain.vo.MaterialReturnWebVo;
import cn.iocoder.wx.module.dimensions.entity.Material;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MaterialService extends IService<Material> {

    MaterialReturnWebVo getMaterialList(MaterialPageVo materialPageVo);

    String insertMaterial(MultipartFile materialZipFile) throws IOException;

    MaterialReturnDTO getMaterialListById(Integer id) throws JsonProcessingException;
}
