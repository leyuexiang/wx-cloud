package cn.iocoder.wx.module.iot.controller.admin.devicebinding;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingRespVO;
import cn.iocoder.wx.module.iot.controller.admin.devicebinding.vo.IotDeviceBindingSaveReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotDeviceBindingDO;
import cn.iocoder.wx.module.iot.service.IotDeviceBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.wx.framework.common.pojo.CommonResult.success;

/** 外部设备绑定管理接口。 */
@Tag(name = "管理后台 - 物联网设备绑定")
@RestController
@RequestMapping("/iot/device-binding")
@Validated
@RequiredArgsConstructor
public class IotDeviceBindingController {

    private final IotDeviceBindingService bindingService;

    @PostMapping("/create")
    @Operation(summary = "创建物联网设备绑定")
    @PreAuthorize("@ss.hasPermission('iot:device-binding:create')")
    public CommonResult<Long> create(@Valid @RequestBody IotDeviceBindingSaveReqVO reqVO) {
        return success(bindingService.createBinding(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改物联网设备绑定")
    @PreAuthorize("@ss.hasPermission('iot:device-binding:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody IotDeviceBindingSaveReqVO reqVO) {
        bindingService.updateBinding(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除物联网设备绑定")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('iot:device-binding:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        bindingService.deleteBinding(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得物联网设备绑定")
    @PreAuthorize("@ss.hasPermission('iot:device-binding:query')")
    public CommonResult<IotDeviceBindingRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(bindingService.getBinding(id), IotDeviceBindingRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得物联网设备绑定列表")
    @PreAuthorize("@ss.hasPermission('iot:device-binding:query')")
    public CommonResult<List<IotDeviceBindingRespVO>> list(String connectorId, Boolean enabled) {
        List<IotDeviceBindingDO> list = bindingService.getBindingList(connectorId, enabled);
        return success(BeanUtils.toBean(list, IotDeviceBindingRespVO.class));
    }
}
