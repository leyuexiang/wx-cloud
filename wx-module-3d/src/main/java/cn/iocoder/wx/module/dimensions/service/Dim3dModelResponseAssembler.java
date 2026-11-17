package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelResponse;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.system.api.user.AdminUserApi;
import cn.iocoder.wx.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** 已授权目录记录的响应组装，创建人及更新人名称统一复用系统用户批量查询。 */
@Component
public class Dim3dModelResponseAssembler {
    private static final int USER_BATCH_SIZE = 200;
    @Resource private AdminUserApi users;

    public Dim3dModelResponse single(Dim3dModel model) { return multiple(List.of(model)).get(0); }

    /** 一页编号去重后最多两次远程查询，空结果不查询用户，避免逐行调用及过长请求地址。 */
    public List<Dim3dModelResponse> multiple(List<Dim3dModel> models) {
        var identifiers = new LinkedHashSet<Long>();
        for (var model : models) {
            if (model.getCreateBy() != null) identifiers.add(model.getCreateBy());
            if (model.getUpdateBy() != null) identifiers.add(model.getUpdateBy());
        }
        var ids = new ArrayList<>(identifiers);
        Map<Long, AdminUserRespDTO> accounts = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += USER_BATCH_SIZE) {
            accounts.putAll(users.getUserMap(ids.subList(offset, Math.min(offset + USER_BATCH_SIZE, ids.size()))));
        }
        return models.stream().map(model -> {
            var creator = accounts.get(model.getCreateBy());
            var updater = accounts.get(model.getUpdateBy());
            // 用户不存在或当前系统查询范围未返回名称时保留空值，禁止使用固定管理员名称替代。
            return Dim3dModelResponse.from(model, creator == null ? null : creator.getNickname(),
                    updater == null ? null : updater.getNickname());
        }).toList();
    }
}
