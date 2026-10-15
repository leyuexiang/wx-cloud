package cn.iocoder.wx.module.dimensions.service.impl;

import cn.hutool.json.JSONUtil;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.dto.SceneDefNoPhotoDTO;
import cn.iocoder.wx.module.dimensions.entity.SceneDef;
import cn.iocoder.wx.module.dimensions.mapper.SceneDefMapper;
import cn.iocoder.wx.module.dimensions.service.SceneDefService;
import cn.iocoder.wx.module.dimensions.service.constant.FileSuffixConstants;
import cn.iocoder.wx.module.dimensions.utils.EntityUtils;
import cn.iocoder.wx.module.dimensions.utils.FileUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipOutputStream;

import static cn.iocoder.wx.module.dimensions.service.impl.FileInfoServiceImpl.addFileToZip;

@Service
@Slf4j
public class SceneDefServiceImpl extends ServiceImpl<SceneDefMapper, SceneDef> implements SceneDefService {

    @Override
    public CommonResult<?> uploadScene(MultipartFile jsonFile,String name ,String photoAddress) throws IOException {
        //判断文件名
        String fileName = FileUtils.getFileName(jsonFile.getOriginalFilename());
        if (StringUtils.isEmpty(fileName)) {
            return CommonResult.error(500,"文件名错误");
        }
        //判断上传文件类型
        String suffix = FileUtils.getFileSuffixLowerCase(jsonFile.getOriginalFilename());
        if (!FileSuffixConstants.JSON.equals(suffix)) {
            return CommonResult.error(500,"文件类型错误,请上传json类型的文件");
        }
        SceneDefNoPhotoDTO sceneDefNoPhoto = JSONUtil.toBean(jsonFile.getInputStream().toString(), SceneDefNoPhotoDTO.class);
        SceneDef sceneDef = new SceneDef();
        BeanUtils.copyProperties(sceneDefNoPhoto, sceneDef);
        sceneDef.setName(name);
        return CommonResult.success(save(sceneDef));
    }

    @Override
    public void downLoadScene(int[] ids, HttpServletResponse response)  {
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
                String MultiPleZipFileName = "Multiple_Scene" + System.currentTimeMillis() + FileSuffixConstants._ZIP;
                response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(MultiPleZipFileName, StandardCharsets.UTF_8));

                //创建zip输出流
                ServletOutputStream os = response.getOutputStream();
                ZipOutputStream zos = new ZipOutputStream(os);

                for (int id : ids) {
                    SceneDef sceneDef = this.baseMapper.selectById(id);
                    SceneDefNoPhotoDTO sceneDefNoPhoto = new SceneDefNoPhotoDTO();
                    EntityUtils.copyProperties(sceneDef,sceneDefNoPhoto);
                    String jsonSceneDefNoPhoto = JSONUtil.toJsonStr(sceneDefNoPhoto);
                    File outPutFile = new File("output.json");
                    try(FileWriter fileWriter = new FileWriter(outPutFile)){
                        fileWriter.write(jsonSceneDefNoPhoto);
                    } catch (IOException ex) {
                        log.error("文件写入失败:{}", ex.getMessage());
                    }

                    //如果传"",就直接下载到压缩包最外层目录下。
                    addFileToZip("", outPutFile, zos);
                    //删除临时文件
                    if(outPutFile.delete()){
                        log.info("临时文件删除成功");
                    }else {
                        log.warn("临时文件删除失败");
                    }
                }
                //完成当前压缩包的写入
                zos.closeEntry();
                //关闭zip输出流流
                zos.close();
                //单个资源下载情况
            } else {
                response.setContentType("application/octet-stream;charset=utf-8");

                SceneDef sceneDef = this.baseMapper.selectById(ids[0]);
                String sceneName = sceneDef.getName();
                //构建json文件名
                String jsonFileName = sceneName + ".json";
                //设置响应头
                response.setHeader("Content-Disposition", "attachment;fileName=" + URLEncoder.encode(jsonFileName, StandardCharsets.UTF_8));

                SceneDefNoPhotoDTO sceneDefNoPhoto = new SceneDefNoPhotoDTO();
                EntityUtils.copyProperties(sceneDef,sceneDefNoPhoto);
                String jsonSceneDefNoPhoto = JSONUtil.toJsonStr(sceneDefNoPhoto);
                File outPutFile = new File("output.json");
                try(FileWriter fileWriter = new FileWriter(outPutFile)){
                    fileWriter.write(jsonSceneDefNoPhoto);
                } catch (IOException ex) {
                    log.error("文件写入失败:{}", ex.getMessage());
                }

                //读取文件内容，并将其写入响应体
                try(
                        FileInputStream fis = new FileInputStream(outPutFile);
                        ServletOutputStream fos = response.getOutputStream()
                ) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while((len = fis.read(buffer)) > 0) {
                        fos.write(buffer,0,len);
                    }

                }catch (IOException ex){
                    ex.printStackTrace();
                }

                //删除临时文件
                if(outPutFile.delete()){
                    log.info("临时文件删除成功");
                }else {
                    log.warn("临时文件删除失败");
                }
            }
        } catch (IOException | IllegalAccessException ex) {
            log.error("文件下载失败:{}", ex.getMessage());
        }
    }

    @Override
    public Boolean judgeSceneName(Integer id, String sceneName) {
        List<SceneDef> list = this.baseMapper.selectList(new LambdaQueryWrapper<SceneDef>()
                .eq(SceneDef::getName, sceneName));
        //除了在数据库中找到重名的，但不包括自己以外，都不需要校验。
        if(!list.isEmpty() && !Objects.equals(id,null)){
            if(!Objects.equals(list.get(0).getId(), id)){
                return Boolean.TRUE;
            }else {
                return Boolean.FALSE;
            }
        }else{
            return Boolean.FALSE;
        }
    }


}