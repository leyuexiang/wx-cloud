package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.domain.dto.MaterialReturnDTO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MaterialPageVo;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MaterialReturnWebVo;
import cn.iocoder.jtyf.module.dimensions.entity.Material;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface MaterialService extends IService<Material> {

    MaterialReturnWebVo getMaterialList(MaterialPageVo materialPageVo);

    String insertMaterial(MultipartFile materialZipFile) throws IOException;

    MaterialReturnDTO getMaterialListById(Integer id) throws JsonProcessingException;
}
