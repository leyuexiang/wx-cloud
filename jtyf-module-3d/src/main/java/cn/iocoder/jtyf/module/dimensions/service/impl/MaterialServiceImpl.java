package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.module.dimensions.domain.dto.MaterialBaseDTO;
import cn.iocoder.jtyf.module.dimensions.domain.dto.MaterialDTO;
import cn.iocoder.jtyf.module.dimensions.domain.dto.MaterialReturnDTO;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MaterialPageVo;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MaterialReturnWebVo;
import cn.iocoder.jtyf.module.dimensions.domain.vo.MaterialWebVo;
import cn.iocoder.jtyf.module.dimensions.entity.Glass;
import cn.iocoder.jtyf.module.dimensions.entity.Material;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.MaterialMapper;
import cn.iocoder.jtyf.module.dimensions.service.MaterialService;
import cn.iocoder.jtyf.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.jtyf.module.dimensions.utils.FileUtils;
import cn.iocoder.jtyf.module.dimensions.utils.ZipUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.jtyf.module.dimensions.service.impl.FileInfoServiceImpl.getFileExtension;


@Service
@Slf4j
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    @Value("${material-path}")
    private String materialPath;

    @Override
    public MaterialReturnWebVo getMaterialList(MaterialPageVo materialPageVo) {
        int page = materialPageVo.getPage() == null ?1:materialPageVo.getPage();
        int size = materialPageVo.getSize() == null ?10:materialPageVo.getSize();
        Page<Material> objectPage = new Page<>(page, size);
        LambdaQueryWrapper<Material> queryWrapper = new LambdaQueryWrapper<>();
        if(materialPageVo.getMaterialName() == null && materialPageVo.getTypeId() == null){
            log.info("查询全部");
        } else if (materialPageVo.getMaterialName() != null && materialPageVo.getTypeId() == null) {
            queryWrapper = new LambdaQueryWrapper<Material>()
                    .like(Material::getMaterialName, materialPageVo.getMaterialName());
        }else if (materialPageVo.getMaterialName() == null){
            queryWrapper = new LambdaQueryWrapper<Material>()
                    .eq(Material::getTypeId, materialPageVo.getTypeId());
        }else {
             queryWrapper = new LambdaQueryWrapper<Material>()
                    .eq(Material::getTypeId, materialPageVo.getTypeId())
                    .like(Material::getMaterialName, materialPageVo.getMaterialName());
        }
        Page<Material> pageDataVos = page(objectPage, queryWrapper);
        MaterialReturnWebVo materialReturnWebVo = new MaterialReturnWebVo();
        List<Material> records = pageDataVos.getRecords();
        materialReturnWebVo.setCountMax((int) pageDataVos.getTotal());
        materialReturnWebVo.setPageMax((int) pageDataVos.getPages());
        ArrayList<MaterialWebVo> materialWebVos = new ArrayList<>();
        for (Material record : records) {
            MaterialWebVo materialWebVo = new MaterialWebVo();
            materialWebVo.setId(record.getId());
            materialWebVo.setMaterialName(record.getMaterialName());
            materialWebVo.setTypeId(record.getTypeId());
            materialWebVo.setPreviews(record.getPreviews());
            materialWebVo.setPhotoAddressPrefix(record.getPhotoAddressPrefix());
            materialWebVos.add(materialWebVo);
        }
        materialReturnWebVo.setMaterialWebVos(materialWebVos);
        return materialReturnWebVo;
    }

    @Override
    public String insertMaterial(MultipartFile materialZipFile) throws IOException {
        //1.先校验压缩包文件名 判断文件名
        String name = FileUtils.getFileName(materialZipFile.getOriginalFilename());
        if (StringUtils.isEmpty(name)) {
            throw JtyfBusinessException.of(ErrorCodeConstants.FILE_NAME_ERROR);
        }
        //判断资源类型
        String suffix = FileUtils.getFileSuffixLowerCase(materialZipFile.getOriginalFilename());
        if (!FileSuffixConstants.ZIP.equals(suffix)) {
            throw JtyfBusinessException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_ZIP);

        }

        // 存储到服务器  解压父目录不存在时
        File uploadZipFolder = new File(materialPath);
        if (!uploadZipFolder.isDirectory()) {
            if (uploadZipFolder.mkdirs()) {
                log.info("文件临时存储目录创建成功");
            } else {
                throw JtyfBusinessException.of(ErrorCodeConstants.FILE_CREATE_DIRECTORY_FAIL);
            }
        }
        Path targetPath = Paths.get(uploadZipFolder.getPath(), materialZipFile.getOriginalFilename());
        try {
            Files.copy(materialZipFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        //3.解压压缩包
        String unZipDirectoryPath = ZipUtils.unzipBuildNameAndDelete(targetPath.toString(), materialPath);

        //4.解析json文件
        File unZipDirFile = new File(unZipDirectoryPath);
        for (File file : Objects.requireNonNull(unZipDirFile.listFiles())) {
            if(getFileExtension(file.getName()).equals("json")) {
                //存表
                Material material = new Material();
                ObjectMapper objectMapper = new ObjectMapper();
                TreeNode rootNode = objectMapper.readTree(file);
                String materialOldName = rootNode.get("materialName").toString();
                String materialName = materialOldName.replaceAll("\"", "");
                String typeId = rootNode.get("typeId").toString();
                String previews = rootNode.get("previews").toString();
                String maps = rootNode.get("maps").toString();
                String glass = rootNode.get("glass").toString();
                material.setMaterialName(materialName);
                material.setTypeId(Integer.parseInt(typeId));
                material.setPreviews(previews);
                material.setMaps(maps);
                material.setGlass(glass);
                //存材质表
                save(material);
                //修改资源文件夹名称为：材质名+id
                String newName = materialName + material.getId();
                String newUnzipDirectoryPath = unZipDirFile.getAbsolutePath().replace(unZipDirFile.getName(), newName);
                File newDirectory = new File(newUnzipDirectoryPath);
                if (unZipDirFile.renameTo(newDirectory)) {
                    Material szl3dMaterial = new Material();
                    szl3dMaterial.setPhotoAddressPrefix( materialPath + newDirectory.getName());
                    update(szl3dMaterial,new LambdaQueryWrapper<Material>()
                            .eq(Material::getId,material.getId()));
                    return "存表及资源文件夹成功改名成功";
                }
            }
        }
        throw JtyfBusinessException.of(ErrorCodeConstants.SAVE_TABLE_ERROR);
    }

    @Override
    public MaterialReturnDTO getMaterialListById(Integer id) throws JsonProcessingException {
        Material material = this.baseMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getId, id));
        MaterialReturnDTO materialReturnDTO = new MaterialReturnDTO();
        materialReturnDTO.setId(material.getId());
        materialReturnDTO.setTypeId(material.getTypeId());
        materialReturnDTO.setMaterialName(material.getMaterialName());
        materialReturnDTO.setPhotoAddressPrefix(material.getPhotoAddressPrefix());
        String previews = material.getPreviews();
        String maps = material.getMaps();
        String glass = material.getGlass();
        ObjectMapper objectMapper = new ObjectMapper();
        MaterialDTO previewsList = objectMapper.readValue(previews, MaterialDTO.class);
        materialReturnDTO.setPreviews(previewsList);
        MaterialBaseDTO[] mapsArray = objectMapper.readValue(maps, MaterialBaseDTO[].class);
        materialReturnDTO.setMaps(mapsArray);
        Glass glassReturn = objectMapper.readValue(glass, Glass.class);
        materialReturnDTO.setGlass(glassReturn);
        return materialReturnDTO;
    }

}
