package cn.iocoder.jtyf.module.member.api.address;

import cn.iocoder.jtyf.framework.common.pojo.CommonResult;
import cn.iocoder.jtyf.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.jtyf.module.member.convert.address.AddressConvert;
import cn.iocoder.jtyf.module.member.service.address.AddressService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

import static cn.iocoder.jtyf.framework.common.pojo.CommonResult.success;

/**
 * 用户收件地址 API 实现类
 *
 * @author 九天芸菲
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class MemberAddressApiImpl implements MemberAddressApi {

    @Resource
    private AddressService addressService;

    @Override
    public CommonResult<MemberAddressRespDTO> getAddress(Long id, Long userId) {
        return success(AddressConvert.INSTANCE.convert02(addressService.getAddress(userId, id)));
    }

    @Override
    public CommonResult<MemberAddressRespDTO> getDefaultAddress(Long userId) {
        return success(AddressConvert.INSTANCE.convert02(addressService.getDefaultUserAddress(userId)));
    }

}
