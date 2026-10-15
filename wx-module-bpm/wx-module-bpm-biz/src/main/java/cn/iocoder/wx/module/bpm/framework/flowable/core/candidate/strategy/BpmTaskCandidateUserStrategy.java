package cn.iocoder.wx.module.bpm.framework.flowable.core.candidate.strategy;

import cn.hutool.core.text.StrPool;
import cn.iocoder.wx.framework.common.util.string.StrUtils;
import cn.iocoder.wx.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.wx.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户 {@link BpmTaskCandidateStrategy} 实现类
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidateUserStrategy extends BpmTaskCandidateAbstractStrategy {

    public BpmTaskCandidateUserStrategy(AdminUserApi adminUserApi) {
        super(adminUserApi);
    }

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.USER;
    }

    @Override
    public void validateParam(String param) {
        adminUserApi.validateUserList(StrUtils.splitToLongSet(param)).checkError();
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        return new LinkedHashSet<>(StrUtils.splitToLong(param, StrPool.COMMA));
    }

}