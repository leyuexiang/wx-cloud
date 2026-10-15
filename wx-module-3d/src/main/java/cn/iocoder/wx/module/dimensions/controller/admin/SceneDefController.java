/*
package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.service.SceneDefService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@RestController
//@RequestMapping("/dimensions/scene")
@Slf4j
@Tag(name = "场景上传和下载接口")
public class SceneDefController {

    @Resource
    SceneDefService sceneDefService;

    //@PostMapping("/upload")
    @Operation(summary = "上传场景")
    //@PreAuthorize("@ss.hasPermission('dimensions:scene:upload')")
    public CommonResult<?> uploadScene(@RequestPart("jsonFile") MultipartFile jsonFile , String name , String photoAddress) throws IOException {
        return sceneDefService.uploadScene(jsonFile,name,photoAddress);
    }

    //@PostMapping("/down-load")
    @Operation(summary = "单个或者批量下载场景")
    //@PreAuthorize("@ss.hasPermission('dimensions:scene:download')")
    public void downLoadScene(@RequestBody int[] ids, HttpServletResponse response) throws UnsupportedEncodingException {
        sceneDefService.downLoadScene(ids,response);
    }

    //@GetMapping("/judge-scene-name")
    @Operation(summary = "判断场景名称是否存在")
    //@PreAuthorize("@ss.hasPermission('dimensions:scene:judge')")
        public CommonResult<?> judgeSceneName(Integer id, String sceneName) {
        return sceneDefService.judgeSceneName(id, sceneName);
    }


}
*/
