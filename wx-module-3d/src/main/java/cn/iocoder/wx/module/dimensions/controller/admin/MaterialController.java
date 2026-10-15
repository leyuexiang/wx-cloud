package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.dto.MaterialReturnDTO;
import cn.iocoder.wx.module.dimensions.domain.vo.MaterialPageVo;
import cn.iocoder.wx.module.dimensions.domain.vo.MaterialReturnWebVo;
import cn.iocoder.wx.module.dimensions.service.MaterialService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/dimensions/material")
@Tag(name = "材质相关接口")
public class MaterialController {

    @Resource
    MaterialService materialService;

    @PostMapping(value = "/insert", consumes = {"multipart/form-data"})
    @Operation(summary = "新增材质信息")
    @PreAuthorize("@ss.hasPermission('dimensions:material:insert')")
    public CommonResult<String> insertMaterial(@RequestPart("materialZipFile") MultipartFile materialZipFile) throws IOException {
        return CommonResult.success(materialService.insertMaterial(materialZipFile));
    }

    @PostMapping("/get-all-list")
    @Operation(summary = "获取材质列表分页信息-可名字和类型id筛选")
    @PreAuthorize("@ss.hasPermission('dimensions:material:getMaterialList')")
    public CommonResult<MaterialReturnWebVo> getMaterialList(@RequestBody MaterialPageVo materialPageVo){
        return CommonResult.success(materialService.getMaterialList(materialPageVo));
    }

    @GetMapping("/select-by-id")
    @Operation(summary = "通过id查询材质信息")
    @PreAuthorize("@ss.hasPermission('dimensions:material:getMaterialListById')")
    public CommonResult<MaterialReturnDTO> getMaterialListById(Integer id) throws JsonProcessingException {
        return CommonResult.success(materialService.getMaterialListById(id));
    }

    @GetMapping("/delete-by-ids")
    @Operation(summary = "通过id列表批量删除材质列表")
    @PreAuthorize("@ss.hasPermission('dimensions:material:delete')")
    public CommonResult<Boolean> deleteById(@RequestBody List<Integer> ids){
        return CommonResult.success(materialService.removeBatchByIds(ids));
    }

}
