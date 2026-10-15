package cn.iocoder.jtyf.module.bpm.framework.flowable.core.candidate.strategy;

import cn.iocoder.jtyf.framework.common.util.string.StrUtils;
import cn.iocoder.jtyf.module.bpm.dal.dataobject.definition.BpmUserGroupDO;
import cn.iocoder.jtyf.module.bpm.framework.flowable.core.candidate.BpmTaskCandidateStrategy;
import cn.iocoder.jtyf.module.bpm.framework.flowable.core.enums.BpmTaskCandidateStrategyEnum;
import cn.iocoder.jtyf.module.bpm.service.definition.BpmUserGroupService;
import cn.iocoder.jtyf.module.system.api.user.AdminUserApi;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static cn.iocoder.jtyf.framework.common.util.collection.CollectionUtils.convertSetByFlatMap;

/**
 * 用户组 {@link BpmTaskCandidateStrategy} 实现类
 *
 * @author kyle
 */
@Component
public class BpmTaskCandidateGroupStrategy extends BpmTaskCandidateAbstractStrategy {

    private final BpmUserGroupService userGroupService;

    public BpmTaskCandidateGroupStrategy(AdminUserApi adminUserApi, BpmUserGroupService userGroupService) {
        super(adminUserApi);
        this.userGroupService = userGroupService;
    }

    @Override
    public BpmTaskCandidateStrategyEnum getStrategy() {
        return BpmTaskCandidateStrategyEnum.USER_GROUP;
    }

    @Override
    public void validateParam(String param) {
        Set<Long> groupIds = StrUtils.splitToLongSet(param);
        userGroupService.getUserGroupList(groupIds);
    }

    @Override
    public Set<Long> calculateUsers(String param) {
        Set<Long> groupIds = StrUtils.splitToLongSet(param);
        List<BpmUserGroupDO> groups = userGroupService.getUserGroupList(groupIds);
        return convertSetByFlatMap(groups, BpmUserGroupDO::getUserIds, Collection::stream);
    }

}