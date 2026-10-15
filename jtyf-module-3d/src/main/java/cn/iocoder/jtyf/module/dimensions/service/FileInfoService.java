package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.dimensions.domain.vo.FileInfoUpdateNameVO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.projectResourceU3DVO;
import cn.iocoder.jtyf.module.dimensions.entity.FileInfo;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

public interface FileInfoService extends IService<FileInfo> {

    void save(String appId) throws IOException;

    void create(String fileName);

    HttpEntity updateResource(String url) throws IOException;

    HttpEntity doPostUpload(MultipartFile uploadFiles, String url) throws IOException;

    String getDownloadPath(Integer parentId, int i);

    Integer deleteFile(Integer parentId, String fileName, Integer id);

    List<FileInfo> selectResourceListByParentId(FileInfo szl3dFileInfo);

    Integer deleteAndSave(String fileAddress) throws IOException;

    boolean validateZipContents(String zipFilePath);

    boolean checkZipFileContainsEntry(String zipFilePath, String targetFileName);

    CommonResult<?> uploadSaveUpdate(String filePath, ResourceInfo resourceInfo) throws IOException;

    CommonResult<?> uploadWeb(MultipartFile[] uploadFiles);

    CommonResult<String> uploadThreeDimension(MultipartFile[] uploadFiles, projectResourceU3DVO projectResourceU3DVo, HttpServletRequest req) throws IOException;

    CommonResult<?> webUploadBanner(MultipartFile[] uploadFiles);

    void down(String uploadPath, HttpServletResponse response) throws IOException;

    void webDown(int[] ids, HttpServletResponse response) throws IOException;

    void webDownMinio(int[] ids, HttpServletResponse response) throws IOException;

    String deleteFileWeb(ResourceInfo resourceInfo);

    Boolean updateFileName(FileInfoUpdateNameVO fileInfoUpdateNameVo);

    void downResource(String uploadPath, HttpServletResponse response);

    void preview(String uploadPath, HttpServletResponse response) throws UnsupportedEncodingException;

    void insertOrUpdate(@RequestBody ResourceInfo resourceInfo, File sourceFile, String artResName, String assetName, String assetBundelName, String uniqueId, String previewPath);

    Integer saveFileInfo(String projectId) throws IOException;
}

