package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.dimensions.entity.BatchConversion;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface BatchConversionService extends IService<BatchConversion> {


    BatchConversion uploadToWeb(MultipartFile uploadFile);

    CommonResult<?> toGltf(Integer id) throws IOException;

    void deleteAll();

    void downloadGltf(int[] ids, HttpServletResponse response);

    Boolean deleteById(Long id);
}
