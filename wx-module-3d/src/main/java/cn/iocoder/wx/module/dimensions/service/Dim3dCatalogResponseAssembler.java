package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dIconResponse;
import cn.iocoder.wx.module.dimensions.domain.vo.Dim3dModelResponse;
import cn.iocoder.wx.module.dimensions.entity.Dim3dIcon;
import cn.iocoder.wx.module.dimensions.entity.Dim3dModel;
import cn.iocoder.wx.module.dimensions.entity.Dim3dResourceBaseEntity;
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
public class Dim3dCatalogResponseAssembler {
    private static final int USER_BATCH_SIZE = 200;
    @Resource private AdminUserApi users;

    public Dim3dModelResponse single(Dim3dModel model) { return multiple(List.of(model)).get(0); }

    public List<Dim3dModelResponse> multiple(List<Dim3dModel> models) {
        return project(models, Dim3dModelResponse::from);
    }

    public Dim3dIconResponse icon(Dim3dIcon icon) { return icons(List.of(icon)).get(0); }

    public List<Dim3dIconResponse> icons(List<Dim3dIcon> icons) {
        return project(icons, Dim3dIconResponse::from);
    }

    @FunctionalInterface
    private interface ResponseFactory<T, R> {
        R create(T record, String creatorName, String updaterName);
    }

    /** 一页编号去重后最多两次远程查询，空结果不查询用户，避免逐行调用及过长请求地址。 */
    private <T extends Dim3dResourceBaseEntity, R> List<R> project(List<T> records, ResponseFactory<T, R> factory) {
        var identifiers = new LinkedHashSet<Long>();
        for (var record : records) {
            if (record.getCreateBy() != null) identifiers.add(record.getCreateBy());
            if (record.getUpdateBy() != null) identifiers.add(record.getUpdateBy());
        }
        var ids = new ArrayList<>(identifiers);
        Map<Long, AdminUserRespDTO> accounts = new HashMap<>();
        for (int offset = 0; offset < ids.size(); offset += USER_BATCH_SIZE) {
            accounts.putAll(users.getUserMap(ids.subList(offset, Math.min(offset + USER_BATCH_SIZE, ids.size()))));
        }
        return records.stream().map(record -> {
            var creator = accounts.get(record.getCreateBy());
            var updater = accounts.get(record.getUpdateBy());
            // 用户不存在或当前系统查询范围未返回名称时保留空值，禁止使用固定管理员名称替代。
            return factory.create(record, creator == null ? null : creator.getNickname(),
                    updater == null ? null : updater.getNickname());
        }).toList();
    }
}
