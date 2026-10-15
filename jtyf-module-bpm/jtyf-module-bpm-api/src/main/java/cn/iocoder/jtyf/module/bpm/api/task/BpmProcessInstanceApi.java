package cn.iocoder.jtyf.module.bpm.api.task;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.jtyf.module.bpm.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME) // TODO 芸菲：fallbackFactory =
@Tag(name = "RPC 服务 - 流程实例")
public interface BpmProcessInstanceApi {

    String PREFIX = ApiConstants.PREFIX + "/process-instance";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建流程实例（提供给内部），返回实例编号")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1")
    CommonResult<String> createProcessInstance(@RequestParam("userId") Long userId,
                                               @Valid @RequestBody BpmProcessInstanceCreateReqDTO reqDTO);

}
