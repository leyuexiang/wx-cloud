package cn.iocoder.wx.module.system.controller.admin.dict;

import cn.iocoder.wx.framework.common.enums.CommonStatusEnum;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.system.controller.admin.dict.vo.data.DictDataTypeRespVO;
import cn.iocoder.wx.module.system.service.dict.DictDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/**
 * 当前前端的公共字典读取适配。管理端前缀由现有网页配置统一添加，
 * 网关只将前端不含平台段的字典读取路径转发到此处，不改变原字典维护接口。
 */
@Tag(name = "管理后台 - 业务字典读取")
@RestController
@RequestMapping("/system/dict/data")
@Validated
public class DictDataTypeController {

    @Resource
    private DictDataService dictDataService;

    @GetMapping("/type/{dictType}")
    @Operation(summary = "按类型读取启用字典项")
    @Parameter(name = "dictType", description = "字典类型", required = true, example = "dim3d_icon_category")
    // 普通业务用户也需要读取分类，只要求已登录；不授予字典管理权限或匿名访问。
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<DictDataTypeRespVO>> getDictDataByType(
            @PathVariable("dictType") @NotBlank @Size(max = 100) String dictType) {
        // 在数据库中按类型和启用状态一次过滤，复用服务的排序；空类型不可退化为全量查询。
        // 单次投影保留编码原值与排序，未知或未配置类型自然返回空数组，不拼造分类选项。
        return success(dictDataService.getDictDataList(CommonStatusEnum.ENABLE.getStatus(), dictType)
                .stream().map(DictDataTypeRespVO::from).toList());
    }
}
