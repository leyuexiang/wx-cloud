package cn.iocoder.wx.module.iot.controller.admin.connector;

import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.framework.common.util.object.BeanUtils;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorSaveReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorRuntimeRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateReqVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotMqttConfigValidateRespVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.service.IotConnectorService;
import cn.iocoder.wx.module.iot.service.IotConnectorManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/** 物联网连接器配置管理接口。 */
@Tag(name = "管理后台 - 物联网连接器")
@RestController
@RequestMapping("/iot/connector")
@Validated
@RequiredArgsConstructor
public class IotConnectorController {

    private final IotConnectorService connectorService;
    private final IotConnectorManagementService managementService;

    /** 配置预校验只使用管理员权限，不访问目标地址或凭据内容。 */
    @PostMapping("/validate-mqtt-config")
    @Operation(summary = "预校验未保存的 MQTT 配置结构")
    @PreAuthorize("@ss.hasAnyPermissions('iot:connector:create', 'iot:connector:update')")
    public CommonResult<IotMqttConfigValidateRespVO> validateMqttConfig(
            @Valid @RequestBody IotMqttConfigValidateReqVO request) {
        return success(managementService.validateMqtt(request));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询物联网连接器")
    @PreAuthorize("@ss.hasPermission('iot:connector:query')")
    public CommonResult<PageResult<IotConnectorRespVO>> page(@Valid IotConnectorPageReqVO request) {
        return success(BeanUtils.toBean(managementService.getPage(request), IotConnectorRespVO.class));
    }

    /** 只按需查询选中连接器；不对列表逐行轮询，避免产生重复数据库请求。 */
    @GetMapping("/runtime")
    @Operation(summary = "查询连接器运行状态与租约观测")
    @PreAuthorize("@ss.hasPermission('iot:connector:query')")
    public CommonResult<IotConnectorRuntimeRespVO> runtime(@Min(1) @RequestParam("id") Long id) {
        return success(managementService.getRuntime(id));
    }

    @PostMapping("/create")
    @Operation(summary = "创建物联网连接器")
    @PreAuthorize("@ss.hasPermission('iot:connector:create')")
    public CommonResult<Long> create(@Valid @RequestBody IotConnectorSaveReqVO reqVO) {
        return success(connectorService.createConnector(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改物联网连接器")
    @PreAuthorize("@ss.hasPermission('iot:connector:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody IotConnectorSaveReqVO reqVO) {
        connectorService.updateConnector(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除物联网连接器")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('iot:connector:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        connectorService.deleteConnector(id);
        return success(true);
    }

    @PostMapping("/enable")
    @Operation(summary = "启用物联网连接器")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('iot:connector:update')")
    public CommonResult<Boolean> enable(@RequestParam("id") Long id) {
        connectorService.enableConnector(id);
        return success(true);
    }

    @PostMapping("/disable")
    @Operation(summary = "停用物联网连接器")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('iot:connector:update')")
    public CommonResult<Boolean> disable(@RequestParam("id") Long id) {
        connectorService.disableConnector(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得物联网连接器")
    @PreAuthorize("@ss.hasPermission('iot:connector:query')")
    public CommonResult<IotConnectorRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(connectorService.getConnector(id), IotConnectorRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得物联网连接器列表")
    @PreAuthorize("@ss.hasPermission('iot:connector:query')")
    public CommonResult<List<IotConnectorRespVO>> list(String connectorId, Boolean enabled) {
        List<IotConnectorDO> list = connectorService.getConnectorList(connectorId, enabled);
        return success(BeanUtils.toBean(list, IotConnectorRespVO.class));
    }
}
