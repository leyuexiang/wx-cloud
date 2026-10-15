package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.model.FileUploadInfo;
import cn.iocoder.wx.module.dimensions.domain.model.UploadUrlsVO;
import cn.iocoder.wx.module.dimensions.entity.FilesMinio;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * 文件表 服务类
 */
public interface IFilesMinioService extends IService<FilesMinio> {

    FileUploadInfo checkFileByMd5(String md5);

    UploadUrlsVO initMultipartUpload(FileUploadInfo fileUploadInfo);

    String mergeMultipartUpload(String md5);

    ResponseEntity<byte[]> downloadMultipartFile(Long id, HttpServletRequest request, HttpServletResponse response) throws Exception;

    List<FilesMinio> getFileList();
}
