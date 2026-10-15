package cn.iocoder.wx.module.vegetation.service;

import cn.iocoder.wx.module.vegetation.api.VegetationPublicationResponse;
import cn.iocoder.wx.module.vegetation.api.VegetationGenerationRequest;
import cn.iocoder.wx.module.vegetation.api.VegetationTaskStatus;
import cn.iocoder.wx.module.vegetation.dal.dataobject.VegetationGenerationTaskDO;
import cn.iocoder.wx.module.vegetation.dal.dataobject.VegetationPublicationDO;
import cn.iocoder.wx.module.vegetation.dal.mysql.VegetationGenerationTaskMapper;
import cn.iocoder.wx.module.vegetation.dal.mysql.VegetationPublicationMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 植被发布版本服务。
 *
 * <p>生成成功只代表不可变文件已落对象存储，只有本服务把一个版本标记为STABLE后，
 * 前端当前场景才会加载它。这样发布、回滚都只切换数据库指针，不触碰已发布文件。</p>
 */
@Service
public class VegetationPublicationService {

    private final VegetationGenerationTaskMapper taskMapper;
    private final VegetationPublicationMapper publicationMapper;
    private final ObjectMapper objectMapper;

    public VegetationPublicationService(VegetationGenerationTaskMapper taskMapper,
                                        VegetationPublicationMapper publicationMapper,
                                        ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.publicationMapper = publicationMapper;
        this.objectMapper = objectMapper;
    }

    /** 把SUCCESS任务原子切换为场景当前版本；重复发布已回滚版本时重新激活它。 */
    @Transactional
    public VegetationPublicationResponse publish(String taskId) {
        VegetationGenerationTaskDO task = findTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("植被生成任务不存在: " + taskId);
        }
        if (!VegetationTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            throw new IllegalArgumentException("只有SUCCESS任务才能发布，当前状态: " + task.getStatus());
        }
        if (task.getPublicationVersion() == null || task.getManifestUrl() == null
                || task.getTilesetUrl() == null) {
            throw new IllegalArgumentException("任务缺少完整发布地址，不能切换为正式版本");
        }

        // 先锁场景而不是只锁当前STABLE记录；没有历史版本的首次发布也必须参与互斥。
        lockScene(task.getSceneId());
        VegetationPublicationDO existing = publicationMapper.selectBySceneIdAndVersion(
                task.getSceneId(), task.getPublicationVersion());
        if (existing != null) {
            if (!"STABLE".equals(existing.getStatus())) {
                // 重复发布曾经回滚的同一不可变版本只切换状态，不重复插入或复制对象存储文件。
                publicationMapper.markScenePublicationsRolledBack(task.getSceneId());
                existing.setStatus("STABLE");
                existing.setUpdateTime(LocalDateTime.now());
                publicationMapper.updateById(existing);
            }
            return toResponse(existing, task.getLayerTilesetUrlsJson(), task.getLayerDensityTilesetUrlsJson());
        }

        // 先下线旧版本，再插入新版本；整个过程在同一事务中，查询不会看到两个STABLE版本。
        publicationMapper.markScenePublicationsRolledBack(task.getSceneId());
        VegetationPublicationDO publication = VegetationPublicationDO.builder()
                .sceneId(task.getSceneId())
                .publicationVersion(task.getPublicationVersion())
                .taskId(task.getId())
                .ruleVersion(task.getRuleVersion())
                .generatorVersion(task.getGeneratorVersion())
                .manifestUrl(task.getManifestUrl())
                .tilesetUrl(task.getTilesetUrl())
                .sourceHash(task.getSourceHash())
                .status("STABLE")
                .instanceCount(task.getInstanceCount() == null ? 0L : task.getInstanceCount())
                .tileCount(task.getTotalTileCount() == null ? 0 : task.getTotalTileCount())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        publicationMapper.insert(publication);
        return toResponse(publication, task.getLayerTilesetUrlsJson(), task.getLayerDensityTilesetUrlsJson());
    }

    /** 查询场景当前稳定版本；没有发布版本时返回空值，由控制器转换为404。 */
    public VegetationPublicationResponse current(String sceneId) {
        VegetationPublicationDO publication = publicationMapper.selectCurrentBySceneId(sceneId);
        return publication == null ? null : toResponse(
                publication,
                layerUrlsForTask(publication.getTaskId()),
                layerDensityUrlsForTask(publication.getTaskId()));
    }

    /** 返回场景全部历史版本，供前端版本选择和审计使用。 */
    public List<VegetationPublicationResponse> list(String sceneId) {
        return publicationMapper.selectBySceneId(sceneId).stream()
                .map(item -> toResponse(item, layerUrlsForTask(item.getTaskId()),
                        layerDensityUrlsForTask(item.getTaskId())))
                .toList();
    }

    /** 把历史版本重新设为STABLE，旧版本只保留ROLLED_BACK状态，不删除文件。 */
    @Transactional
    public VegetationPublicationResponse rollback(String sceneId, String publicationVersion) {
        lockScene(sceneId);
        VegetationPublicationDO target = publicationMapper.selectBySceneIdAndVersion(sceneId, publicationVersion);
        if (target == null) {
            throw new IllegalArgumentException("植被发布版本不存在: " + publicationVersion);
        }
        publicationMapper.markScenePublicationsRolledBack(sceneId);
        target.setStatus("STABLE");
        target.setUpdateTime(LocalDateTime.now());
        publicationMapper.updateById(target);
        VegetationGenerationTaskDO task = taskMapper.selectById(target.getTaskId());
        if (task != null) {
            task.setPublicationVersion(target.getPublicationVersion());
            task.setManifestUrl(target.getManifestUrl());
            task.setTilesetUrl(target.getTilesetUrl());
            taskMapper.updateById(task);
        }
        return toResponse(target, layerUrlsForTask(target.getTaskId()),
                layerDensityUrlsForTask(target.getTaskId()));
    }

    /** 创建并锁定场景行，兼容首次发布没有可供FOR UPDATE锁定的发布记录。 */
    private void lockScene(String sceneId) {
        publicationMapper.ensureSceneLock(sceneId);
        if (publicationMapper.lockScene(sceneId) == null) {
            throw new IllegalStateException("无法获取植被场景发布锁: " + sceneId);
        }
    }

    private VegetationGenerationTaskDO findTask(String taskId) {
        try {
            VegetationGenerationTaskDO task = taskMapper.selectById(Long.valueOf(taskId));
            if (task != null) return task;
        } catch (NumberFormatException ignored) {
            // 远端任务号可以是非数字字符串，继续按generator_task_id查询。
        }
        return taskMapper.selectByGeneratorTaskId(taskId);
    }

    private String layerUrlsForTask(Long taskId) {
        if (taskId == null) return null;
        VegetationGenerationTaskDO task = taskMapper.selectById(taskId);
        return task == null ? null : task.getLayerTilesetUrlsJson();
    }

    /** 多档入口与旧图层入口分列保存，历史任务为空时由前端回退到单入口。 */
    private String layerDensityUrlsForTask(Long taskId) {
        if (taskId == null) return null;
        VegetationGenerationTaskDO task = taskMapper.selectById(taskId);
        return task == null ? null : task.getLayerDensityTilesetUrlsJson();
    }

    private VegetationPublicationResponse toResponse(VegetationPublicationDO publication, String layerUrlsJson,
                                                     String layerDensityUrlsJson) {
        Map<String, VegetationGenerationRequest.DensityProfile> densityProfiles = Map.of();
        if (publication.getTaskId() != null) {
            VegetationGenerationTaskDO task = taskMapper.selectById(publication.getTaskId());
            if (task != null && task.getSnapshotJson() != null) {
                try {
                    var snapshot = objectMapper.readTree(task.getSnapshotJson());
                    var profiles = snapshot.get("densityProfiles");
                    if (profiles != null && profiles.isObject()) {
                        densityProfiles = VegetationGenerationRequest.withDefaultTileSizes(
                                objectMapper.convertValue(profiles, objectMapper.getTypeFactory()
                                        .constructMapType(Map.class, String.class,
                                                VegetationGenerationRequest.DensityProfile.class)));
                    }
                } catch (JsonProcessingException exception) {
                    // 旧任务快照没有密度字段时保持空映射，前端仍可加载旧发布版本。
                }
            }
        }
        return new VegetationPublicationResponse(
                publication.getSceneId(), publication.getPublicationVersion(), publication.getTaskId(),
                publication.getRuleVersion(), publication.getGeneratorVersion(), publication.getManifestUrl(),
                publication.getTilesetUrl(), parseLayerUrls(layerUrlsJson),
                parseLayerDensityUrls(layerDensityUrlsJson), publication.getSourceHash(),
                publication.getStatus(), publication.getInstanceCount(), publication.getTileCount(), densityProfiles);
    }

    private Map<String, String> parseLayerUrls(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    /** 解析每图层三档入口；损坏记录不影响旧发布继续通过单入口显示。 */
    private Map<String, Map<String, String>> parseLayerDensityUrls(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }
}
