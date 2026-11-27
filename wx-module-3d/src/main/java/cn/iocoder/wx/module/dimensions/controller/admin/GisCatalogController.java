package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.vo.GisCatalogEntry;
import cn.iocoder.wx.module.dimensions.service.ArtResourceAccess;
import cn.iocoder.wx.module.dimensions.service.GisCatalogService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static cn.iocoder.wx.module.dimensions.config.Dim3dPlatformConfiguration.PLATFORM_SEGMENT;

/** 共享公开 GIS 来源只对有效登录身份列举，双端路由复用现有身份拦截器。 */
@RestController
@RequestMapping("/dim3d")
public class GisCatalogController {
    @Resource private ArtResourceAccess access;
    @Resource private GisCatalogService catalog;

    @GetMapping({"/webgis/info/list", PLATFORM_SEGMENT + "/webgis/info/list"})
    public CommonResult<List<GisCatalogEntry>> list(@RequestParam("dataType") String dataType) {
        access.actor();
        return CommonResult.success(catalog.list(dataType));
    }
}
