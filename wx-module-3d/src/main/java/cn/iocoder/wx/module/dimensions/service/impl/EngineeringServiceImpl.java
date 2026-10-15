package cn.iocoder.wx.module.dimensions.service.impl;


import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.module.dimensions.controller.admin.TaskMessageController;
import cn.iocoder.wx.module.dimensions.domain.vo.*;
import cn.iocoder.wx.module.dimensions.entity.Engineering;
import cn.iocoder.wx.module.dimensions.entity.EngineeringDataListRow;
import cn.iocoder.wx.module.dimensions.entity.SceneDef;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.EngineeringMapper;
import cn.iocoder.wx.module.dimensions.service.EngineeringDataListRowService;
import cn.iocoder.wx.module.dimensions.service.EngineeringService;
import cn.iocoder.wx.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.wx.module.dimensions.utils.FileUtils;
import cn.iocoder.wx.module.dimensions.utils.SecurityUtil;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class EngineeringServiceImpl extends ServiceImpl<EngineeringMapper, Engineering> implements EngineeringService {
    private static final String commStr = "{\n" +
            "    \"CameraComponent\": \"[\\r\\n  {\\r\\n    \\\"componentName\\\": \\\"CameraComponent\\\",\\r\\n    \\\"property\\\": {\\r\\n      \\\"LocalPosition\\\": {\\r\\n        \\\"x\\\": 0.0,\\r\\n        \\\"y\\\": 10.0,\\r\\n        \\\"z\\\": -20.0\\r\\n      },\\r\\n      \\\"LocalRotation\\\": {\\r\\n        \\\"x\\\": 20.0000038,\\r\\n        \\\"y\\\": 0.0,\\r\\n        \\\"z\\\": 0.0\\r\\n      },\\r\\n      \\\"FieldOfView\\\": 60.0,\\r\\n      \\\"FocusPointOffset\\\": 1.2,\\r\\n      \\\"NearClippingPlanes\\\": 0.3,\\r\\n      \\\"FarClippingPlanes\\\": 10000.0,\\r\\n      \\\"MoveSpeed\\\": 6.0,\\r\\n      \\\"MoveAccelerationRate\\\": 15.0,\\r\\n      \\\"OrbitEnable\\\": true,\\r\\n      \\\"OrbitSensitivity\\\": 0.2,\\r\\n      \\\"ZoomEnable\\\": true,\\r\\n      \\\"PerspStandardZoomSensitivity\\\": 1.0,\\r\\n      \\\"OrthoStandardZoomSensitivity\\\": 1.0,\\r\\n      \\\"IsPanningEnabled\\\": true,\\r\\n      \\\"StandardPanSensitivity\\\": 0.2,\\r\\n      \\\"__type\\\": \\\"CameraComponent\\\"\\r\\n    }\\r\\n  }\\r\\n]\",\n" +
            "    \"SkyBoxComponent\": \"[\\r\\n  {\\r\\n    \\\"componentName\\\": \\\"SkyBoxComponent\\\",\\r\\n    \\\"property\\\": {\\r\\n      \\\"SkyboxEnable\\\": true,\\r\\n      \\\"BackgroundStyle\\\": 1,\\r\\n      \\\"ExpanseVolumetricCloudSkyStyle\\\": 0,\\r\\n      \\\"PictureCheck\\\": true,\\r\\n      \\\"SkyPicture\\\": 0,\\r\\n      \\\"ColorType\\\": true,\\r\\n      \\\"Colors\\\": [\\r\\n        {\\r\\n          \\\"r\\\": 127.5,\\r\\n          \\\"g\\\": 127.5,\\r\\n          \\\"b\\\": 127.5,\\r\\n          \\\"a\\\": 1.0\\r\\n        }\\r\\n      ],\\r\\n      \\\"DLightEnable\\\": true,\\r\\n      \\\"DLightIntensity\\\": 24305.0,\\r\\n      \\\"DLightAngle\\\": 48.1400032,\\r\\n      \\\"DLightColor\\\": {\\r\\n        \\\"r\\\": 255.0,\\r\\n        \\\"g\\\": 255.0,\\r\\n        \\\"b\\\": 255.0,\\r\\n        \\\"a\\\": 1.0\\r\\n      },\\r\\n      \\\"__type\\\": \\\"SkyBoxComponent\\\"\\r\\n    }\\r\\n  }\\r\\n]\",\n" +
            "    \"EffectComponent\": \"[\\r\\n  {\\r\\n    \\\"componentName\\\": \\\"EffectComponent\\\",\\r\\n    \\\"property\\\": {\\r\\n      \\\"ParticleFireEnable\\\": false,\\r\\n      \\\"ParticleFireColor\\\": {\\r\\n        \\\"r\\\": 255.0,\\r\\n        \\\"g\\\": 255.0,\\r\\n        \\\"b\\\": 255.0,\\r\\n        \\\"a\\\": 1.0\\r\\n      },\\r\\n      \\\"RunningLineEnable\\\": false,\\r\\n      \\\"RunningLineColor\\\": {\\r\\n        \\\"r\\\": 255.0,\\r\\n        \\\"g\\\": 255.0,\\r\\n        \\\"b\\\": 255.0,\\r\\n        \\\"a\\\": 1.0\\r\\n      },\\r\\n      \\\"SunshineEnable\\\": false,\\r\\n      \\\"SunshineColor\\\": {\\r\\n        \\\"r\\\": 255.0,\\r\\n        \\\"g\\\": 255.0,\\r\\n        \\\"b\\\": 255.0,\\r\\n        \\\"a\\\": 1.0\\r\\n      },\\r\\n      \\\"FogEnable\\\": false,\\r\\n      \\\"FogColor\\\": {\\r\\n        \\\"r\\\": 255.0,\\r\\n        \\\"g\\\": 255.0,\\r\\n        \\\"b\\\": 255.0,\\r\\n        \\\"a\\\": 1.0\\r\\n      },\\r\\n      \\\"__type\\\": \\\"EffectComponent\\\"\\r\\n    }\\r\\n  }\\r\\n]\"\n" +
            "  }";
    // 注入配置项
    @Value("${preview.2d.base-url}")
    private String baseUrl2d;

    @Value("${preview.3d.base-url}")
    private String baseUrl3d;

    @Resource
    EngineeringDataListRowService engineeringDataListRowService;

    @Override
    public Integer create(Engineering engineering) {

        try {
            engineering.setStatus(1);
            engineering.setVersion(1);
            if (engineering.getIsPublish() == null) {
                engineering.setIsPublish(0);
            }
            engineering.setCommComJsons(commStr);
            engineering.setEditor(SecurityUtil.getUsername());
            this.baseMapper.insert(engineering);

            Integer maxId = this.baseMapper.findMaxId();

            String[] rowNameArray = {"id", "name", "lng", "lat"};

            for (int i = 0; i < 4; i++) {
                EngineeringDataListRow engineeringDataListRow = new EngineeringDataListRow();
                engineeringDataListRow.setProjectEngineeringId(maxId);
                engineeringDataListRow.setStatus(1);
                engineeringDataListRow.setRowSort(i + 1);
                engineeringDataListRow.setRowName(rowNameArray[i]);
                engineeringDataListRowService.save(engineeringDataListRow);
            }
            return 2;
        } catch (Exception e) {
            log.error("新建场景失败：{}", e.getMessage());
            return 1;
        }
    }

    @Override
    public List<ProjectEngineeringVO> find(LambdaQueryWrapper<Engineering> lambdaQueryWrapper, boolean findOne) {

        List<Engineering> list = this.baseMapper.selectList(lambdaQueryWrapper);
        LinkedList<ProjectEngineeringVO> projectEngineeringVos = new LinkedList<>();
        for (Engineering eng : list) {
            ProjectEngineeringVO pev = new ProjectEngineeringVO();
            pev.setId(eng.getId());
            pev.setProject(eng.getProject());
            pev.setName(eng.getName());
            pev.setIsPublish(eng.getIsPublish());
            if (findOne) {
                pev.setNodes(eng.getNodes() == null ? null : JSONObject.parse(eng.getNodes()));
                pev.setComPoints(eng.getComPoints() == null ? null : JSONObject.parse(eng.getComPoints()));
                pev.setGisInfo(eng.getGisInfo() == null ? null : JSONObject.parse(eng.getGisInfo()));
                pev.setReleaseStateUuid(eng.getReleaseStateUuid());
                pev.setStateInfos(eng.getStateInfos() == null ? "" : JSONObject.parse(eng.getStateInfos()));
                pev.setLockedGameobjectList(eng.getLockedGameObjectList() == null ? new ArrayList<>() : eng.getLockedGameObjectList().split(","));
                pev.setCameraInitialEuler(eng.getCameraInitialEuler() == null ? null : JSONObject.parse(eng.getCameraInitialEuler()));
                pev.setCameraInitialPosition(eng.getCameraInitialPosition() == null ? null : JSONObject.parse(eng.getCameraInitialPosition()));
                pev.setCommComJsons(eng.getCommComJsons() == null ? "" : JSONObject.parse(eng.getCommComJsons()));
                pev.setComsInstance(eng.getComsInstance() == null ? "" : JSONObject.parse(eng.getComsInstance()));
                pev.setHotfixComponents(eng.getHotfixComponents() == null ? "" : JSONObject.parse(eng.getHotfixComponents()));
                pev.setDicComSystemInfo(eng.getDicComSystemInfo() == null ? "" : JSONObject.parse(eng.getDicComSystemInfo()));
                pev.setUploadTime(eng.getUploadTime() == null ? null : eng.getUploadTime());
                pev.setSceneConfigInfo(eng.getSceneConfigInfo() == null ? "" : JSONObject.parse(eng.getSceneConfigInfo()));
                pev.setIconInfos(eng.getIconInfos() == null ? "" : JSONObject.parse(eng.getIconInfos()));
            }
            pev.setSceneResId(eng.getSceneResId());
            pev.setType(eng.getType());
            pev.setVersion(eng.getVersion());
            pev.setStatus(eng.getStatus());
            pev.setEditor(eng.getEditor());
            pev.setUpdateTime(eng.getUpdateTime());
            pev.setPhotoAddress(eng.getPhotoAddress());
            projectEngineeringVos.add(pev);
        }
        return projectEngineeringVos;
    }

    @Override
    public void setCollection(ProjectEngineeringCollectionVO projectEngineeringCollectionVo) {
        Engineering engineering = this.baseMapper.selectById(projectEngineeringCollectionVo.getId());
        this.baseMapper.updateById(engineering);
    }

    @Override
    public void setPublish(ProjectEngineeringPublishVO projectEngineeringPublishVo) {
        Engineering engineering = this.baseMapper.selectById(projectEngineeringPublishVo.getId());
        engineering.setIsPublish(projectEngineeringPublishVo.getIsPublish());
        this.baseMapper.updateById(engineering);
    }

    @Override
    public void change(ProjectEngineeringCreateVO pecv) {
        Object comm = JSONObject.parse(commStr);
        Engineering engineering = new Engineering();
        engineering.setId(pecv.getId());
        engineering.setProject(pecv.getProject());
        engineering.setComPoints((pecv.getComPoints() == null ? "" : JSONObject.toJSONString(pecv.getComPoints())));
        engineering.setNodes((pecv.getNodes() == null ? "" : JSONObject.toJSONString(pecv.getNodes())));
        engineering.setGisInfo(pecv.getGisInfo() == null ? "" : JSONObject.toJSONString(pecv.getGisInfo()));
        engineering.setName(pecv.getName());
        engineering.setType(pecv.getType());
        engineering.setReleaseStateUuid(pecv.getReleaseStateUuid());
        engineering.setStateInfos(pecv.getStateInfos() == null ? "" : JSONObject.toJSONString(pecv.getStateInfos()));
        engineering.setLockedGameObjectList(pecv.getLockedGameobjectList().isEmpty() ? null : String.join(",", pecv.getLockedGameobjectList()));
        engineering.setCameraInitialEuler(pecv.getCameraInitialEuler() == null ? "" : JSONObject.toJSONString(pecv.getCameraInitialEuler()));
        engineering.setCameraInitialPosition(pecv.getCameraInitialPosition() == null ? "" : JSONObject.toJSONString(pecv.getCameraInitialPosition()));
        engineering.setCommComJsons(pecv.getCommComJsons() == null ? JSONObject.toJSONString(comm) : JSONObject.toJSONString(pecv.getCommComJsons()));
        engineering.setSceneResId(pecv.getSceneResId());
        engineering.setComsInstance(pecv.getComsInstance() == null ? "" : JSONObject.toJSONString(pecv.getComsInstance()));
        engineering.setHotfixComponents(pecv.getHotfixComponents() == null ? "" : JSONObject.toJSONString(pecv.getHotfixComponents()));
        engineering.setDicComSystemInfo(pecv.getDicComSystemInfo() == null ? "" : JSONObject.toJSONString(pecv.getDicComSystemInfo()));
        engineering.setSceneConfigInfo(pecv.getSceneConfigInfo() == null ? "" : JSONObject.toJSONString(pecv.getSceneConfigInfo()));
        engineering.setIconInfos(pecv.getIconInfos() == null ? "" : JSONObject.toJSONString(pecv.getIconInfos()));
        this.baseMapper.updateById(engineering);
    }

    @Override
    public Integer setName(Integer id, String name) {
        Engineering engineering = this.baseMapper.selectById(id);
        if (engineering == null) {
            return 1;
        }
        engineering.setName(name);
        this.baseMapper.updateById(engineering);
        return 2;
    }

    @Override
    public List<ProjectEngineeringVO> getSceneList(String platformType, String sort, Integer id, String project, String name, String nodes, String comsInstance, String type, Integer isPublish, String findType) {
        LambdaQueryWrapper<Engineering> queryWrapper = new LambdaQueryWrapper<Engineering>()
                .eq(Engineering::getPlatformType, platformType)
                .eq(Engineering::getStatus, 1)
                .eq(Objects.nonNull(id), Engineering::getId, id)
                .eq(Objects.nonNull(project), Engineering::getProject, project)
                .like(Objects.nonNull(name), Engineering::getName, name)
                .eq(Objects.nonNull(nodes), Engineering::getNodes, nodes)
                .eq(Objects.nonNull(comsInstance), Engineering::getSceneResId, comsInstance)
                .eq(Objects.nonNull(type), Engineering::getType, type)
                .eq(Objects.nonNull(isPublish), Engineering::getIsPublish, isPublish);
        if (sort != null) {
            switch (sort) {
                case "1":
                    queryWrapper.orderBy(true, true, Engineering::getUpdateTime);
                    break;
                case "2":
                    queryWrapper.orderBy(true, false, Engineering::getUpdateTime);
                    break;
            }
        }
        List<ProjectEngineeringVO> projectEngineeringVoList = find(queryWrapper, Objects.nonNull(id));
        if (findType != null) {
            ProjectEngineeringVO p = projectEngineeringVoList.isEmpty() ? new ProjectEngineeringVO() : projectEngineeringVoList.get(0);
            p.setProject(p.getProject() == null ? "" : p.getProject());
            p.setComsInstance(p.getComsInstance() == null ? new cn.hutool.json.JSONObject() : p.getComsInstance());
            p.setNodes(p.getNodes() == null ? new cn.hutool.json.JSONObject() : p.getNodes());
            p.setUploadTime(p.getUploadTime() == null ? null : p.getUploadTime());
            projectEngineeringVoList = new ArrayList<>();
            projectEngineeringVoList.add(p);
        }
        return projectEngineeringVoList;
    }

    @Override
    public Engineering copyScene(String id) {
        Engineering engineering = this.baseMapper.selectById(Integer.parseInt(id));
        engineering.setId(null);
        engineering.setName(engineering.getName() + "_copy");
        this.baseMapper.insert(engineering);
        return engineering;
    }


    private String getJsonStr() {
        try {
            InputStream templateInputStream = TaskMessageController.class.getClassLoader().getResource("engineering/comm.json").openStream();
            // 读取JSON文件内容
            return new String(templateInputStream.readAllBytes());
        } catch (Exception e) {
            log.error("文件获取异常：{}", e.getMessage());
        }
        return null;
    }


    @Override
    public int uploadOrUpdateScene(String platformType, MultipartFile jsonFile, Integer id, String name, String photoAddress) throws IOException {
        if (id != null) {
            Engineering engineering = this.baseMapper.selectById(id);
            if (name != null) {
                engineering.setName(name);
            }

            engineering.setPhotoAddress(Objects.requireNonNullElse(photoAddress, ""));

            return this.baseMapper.updateById(engineering);
        }

        Engineering engineering = new Engineering();
        if (jsonFile != null) {
            //判断文件名
            String fileName = FileUtils.getFileName(jsonFile.getOriginalFilename());
            if (StringUtils.isEmpty(fileName)) {
                throw WxBusinessException.of(ErrorCodeConstants.FILE_NAME_ERROR);
            }
            //判断上传文件类型
            String suffix = FileUtils.getFileSuffixLowerCase(jsonFile.getOriginalFilename());
            if (!FileSuffixConstants.JSON.equals(suffix)) {
                throw WxBusinessException.of(ErrorCodeConstants.FILE_TYPE_ERROR_PLEASE_JSON);

            }

            //添加文件校验
            SceneDef sceneDef = parseSceneDefFile(jsonFile);

            BeanUtils.copyProperties(sceneDef, engineering);
        }

        if (engineering.getStatus() == null) {
            engineering.setStatus(1);
        }
        if (engineering.getVersion() == null) {
            engineering.setVersion(1);
        }
        if (engineering.getIsPublish() == null) {
            engineering.setIsPublish(0);
        }
        if (engineering.getEditor() == null) {
            engineering.setEditor(SecurityUtil.getUsername());
        }
        if (engineering.getCommComJsons() == null) {
            engineering.setCommComJsons(commStr);
        }
        engineering.setName(name);
        engineering.setPhotoAddress(photoAddress);
        engineering.setPlatformType(platformType);
        engineering.setId(null);
        save(engineering);
        return 1;
    }

    private SceneDef parseSceneDefFile(MultipartFile jsonFile) throws IOException {
        // 1. 检查文件是否为空
        if (jsonFile == null || jsonFile.isEmpty()) {
            throw WxBusinessException.of(ErrorCodeConstants.UPLOADED_JSON_FILE_CANNOT_NULL);
        }

        // 2. 检查文件大小是否合理（可选，根据实际需求调整）
        if (jsonFile.getSize() > 10 * 1024 * 1024) { // 限制10MB
            throw WxBusinessException.of(ErrorCodeConstants.UPLOADED_FILE_TOO_LARGE);
        }

        // 3. 读取文件内容并校验
        String jsonContent = "";
        try (InputStream inputStream = jsonFile.getInputStream()) {
            jsonContent = IoUtil.read(inputStream, StandardCharsets.UTF_8);

            // 4. 检查内容是否为空或仅包含空白字符
            if (StrUtil.isBlank(jsonContent)) {
                throw WxBusinessException.of(ErrorCodeConstants.JSON_FILE_EMPTY);
            }

            // 5. 检查是否是合法的JSON格式（预解析）
            if (!isValidJson(jsonContent)) {
                throw WxBusinessException.of(ErrorCodeConstants.INVALID_JSON_FORMAT);
            }

            // 6. 转换为对象
            return JSONUtil.toBean(jsonContent, SceneDef.class);
        } catch (JSONException e) {
            // 处理JSON解析异常
            log.error("JSON解析失败: {}", jsonContent, e);
            throw WxBusinessException.of(ErrorCodeConstants.INVALID_JSON_FORMAT);
        } catch (IOException e) {
            // 处理文件读取异常
            log.error("读取JSON文件失败", e);
            throw new IOException("读取JSON文件失败", e);
        }
    }

    private boolean isValidJson(String jsonStr) {
        try {
            JSONUtil.parse(jsonStr);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public void downLoadScene(int[] ids, HttpServletResponse response) {
        if (ids.length == 0) {
            log.warn("单个或者批量下载场景失败:ids为空");
            return;
        }

        try {
            //批量下载情况
            if (ids.length > 1) {
                //设置响应头信息
                response.setContentType("application/zip");
                //设置响应头
                String multiPleZipFileName = "Multiple_Scene" + System.currentTimeMillis() + FileSuffixConstants._ZIP;
                response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(multiPleZipFileName, StandardCharsets.UTF_8));

                //创建zip输出流
                ServletOutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os);

                for (int id : ids) {
                    Engineering engineering = this.baseMapper.selectById(id);
                    SceneDef sceneDef = new SceneDef();
                    String[] excludeProperties = {"editor", "photoAddress", "serialVersionUID"};
                    BeanUtils.copyProperties(engineering, sceneDef, excludeProperties);
                    String jsonSceneString = JSONUtil.toJsonStr(sceneDef);
                    zos.putNextEntry(new ZipEntry(engineering.getName() + ".json"));
                    zos.write(jsonSceneString.getBytes());
                }
                //完成当前压缩包的写入
                zos.closeEntry();
                //关闭zip输出流流
                zos.close();
            } else {//单个资源下载情况
                response.setContentType("application/octet-stream;charset=utf-8");

                Engineering engineering = this.baseMapper.selectById(ids[0]);
                String sceneName = engineering.getName();
                //构建json文件名
                String jsonFileName = sceneName + ".json";
                //设置响应头
                response.setHeader("Content-Disposition", "attachment;fileName=" +
                        URLEncoder.encode(jsonFileName, StandardCharsets.UTF_8));

                SceneDef sceneDef = new SceneDef();
                String[] excludeProperties = {"editor", "photoAddress", "serialVersionUID"};
                BeanUtils.copyProperties(engineering, sceneDef, excludeProperties);
                String jsonSceneDefString = JSONUtil.toJsonStr(sceneDef);
                File outPutFile = new File("output.json");
                try (FileWriter fileWriter = new FileWriter(outPutFile)) {
                    fileWriter.write(jsonSceneDefString);
                } catch (IOException e) {
                    log.error("写入文件时发生错误:{}", e.getMessage());
                }

                //读取文件内容，并将其写入响应体
                try (
                        FileInputStream fis = new FileInputStream(outPutFile);
                        ServletOutputStream fos = response.getOutputStream()
                ) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }

                } catch (IOException e) {
                    log.error("读取文件时发生错误:{}", e.getMessage());
                }

                //删除临时文件
                if (outPutFile.delete()) {
                    log.info("临时文件删除成功");
                } else {
                    log.info("临时文件删除失败");
                }
            }

        } catch (IOException e) {
            log.error("下载文件时发生错误:{}", e.getMessage());
        }
    }

    @Override
    public Boolean judgeSceneName(String platformType, Integer id, String sceneName) {
        List<Engineering> engineeringList = this.baseMapper.selectList(new LambdaQueryWrapper<Engineering>()
                .eq(Engineering::getPlatformType, platformType)
                .eq(Engineering::getName, sceneName)
        );
        //除了在数据库中找到重名的，但不包括自己以外，都不需要校验。
        if (!engineeringList.isEmpty() && !Objects.equals(id, null)) {
            if (!Objects.equals(engineeringList.get(0).getId(), id)) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } else {
            if (id == null && !engineeringList.isEmpty()) {
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }
    }

    @Override
    public Boolean updateWebSceneInfo(ProjectEngineeringUpdateVO projectEngineeringUpdateVO) {

        Engineering engineering = this.baseMapper.selectById(projectEngineeringUpdateVO.getId());
        if (engineering == null) {
            throw WxBusinessException.of(ErrorCodeConstants.SCENE_NOT_FOUND);
        }

        if (!projectEngineeringUpdateVO.getPlatformType().equals(engineering.getPlatformType())) {
            throw WxBusinessException.of(ErrorCodeConstants.SCENE_RESOURCE_INCORRECT_PLEASE_RETRY);
        }

        if (!StringUtils.isBlank(projectEngineeringUpdateVO.getComsInstance())) {
            engineering.setComsInstance(projectEngineeringUpdateVO.getComsInstance());
        }

        if (!StringUtils.isBlank(projectEngineeringUpdateVO.getSceneConfigInfo())) {
            engineering.setSceneConfigInfo(projectEngineeringUpdateVO.getSceneConfigInfo());
        }

        this.baseMapper.updateById(engineering);

        return Boolean.TRUE;
    }

    @Override
    public List<IsPublishVO> getIsPublishList() {
        // 查询已发布的工程
        List<Engineering> engineeringList = lambdaQuery().eq(Engineering::getIsPublish, 1).list();
        // 空集合快速返回
        if (CollectionUtils.isEmpty(engineeringList)) {
            return Collections.emptyList();
        }

        // 使用Stream API进行转换
        return engineeringList.stream()
                .map(this::convertToVO)
                .filter(Objects::nonNull) // 过滤可能的空值
                .collect(Collectors.toList());
    }

    // 转换工程实体为VO
    private IsPublishVO convertToVO(Engineering engineering) {
        try {
            IsPublishVO vo = new IsPublishVO();
            BeanUtils.copyProperties(engineering, vo);

            // 标准化平台类型
            String platformType = "3d-web".equals(vo.getPlatformType()) ? "2.5d" : "3d";
            vo.setPlatformType(platformType);

            // 构建预览URL
            vo.setUrl(buildPreviewUrl(platformType, vo.getId()));

            return vo;
        } catch (Exception e) {
            log.error("工程转换失败, id: {}", engineering.getId(), e);
            return null; // 过滤转换失败的记录
        }
    }
    // 构建预览URL
    private String buildPreviewUrl(String platformType , Integer id) {
        if ("2.5d".equals(platformType)) {
            return UriComponentsBuilder
                    .fromHttpUrl(baseUrl2d)
                    .queryParam("id", id)
                    .queryParam("mode", "preview")
                    .build()
                    .toUriString();
        } else {
            String fragmentParams = String.format("?type=u3d&key=%s&isInXS=%s", id, false);
            return baseUrl3d + "?" + fragmentParams;
        }
    }

}

