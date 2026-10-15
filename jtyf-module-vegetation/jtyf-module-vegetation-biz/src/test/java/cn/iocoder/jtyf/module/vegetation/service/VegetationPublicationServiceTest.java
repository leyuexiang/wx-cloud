package cn.iocoder.jtyf.module.vegetation.service;

import cn.iocoder.jtyf.module.vegetation.api.VegetationPublicationResponse;
import cn.iocoder.jtyf.module.vegetation.api.VegetationTaskStatus;
import cn.iocoder.jtyf.module.vegetation.dal.dataobject.VegetationGenerationTaskDO;
import cn.iocoder.jtyf.module.vegetation.dal.dataobject.VegetationPublicationDO;
import cn.iocoder.jtyf.module.vegetation.dal.mysql.VegetationGenerationTaskMapper;
import cn.iocoder.jtyf.module.vegetation.dal.mysql.VegetationPublicationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 发布和回滚的一致性测试；重点验证场景锁先于版本切换，并且回滚版本可再次发布。 */
@ExtendWith(MockitoExtension.class)
class VegetationPublicationServiceTest {

    @Mock
    private VegetationGenerationTaskMapper taskMapper;
    @Mock
    private VegetationPublicationMapper publicationMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishReactivatesRolledBackVersionWithoutInsertingDuplicate() throws Exception {
        VegetationGenerationTaskDO task = successTask();
        VegetationPublicationDO existing = publication("ROLLED_BACK");
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(publicationMapper.ensureSceneLock("scene-lock")).thenReturn(1);
        when(publicationMapper.lockScene("scene-lock")).thenReturn("scene-lock");
        when(publicationMapper.selectBySceneIdAndVersion("scene-lock", "publication-1"))
                .thenReturn(existing);
        when(taskMapper.selectById(7L)).thenReturn(task);

        VegetationPublicationService service = new VegetationPublicationService(
                taskMapper, publicationMapper, objectMapper);
        VegetationPublicationResponse response = service.publish("7");

        assertEquals("STABLE", response.status());
        assertEquals("STABLE", existing.getStatus());
        assertEquals("/files/forest/density/performance/tileset.json",
                response.layerDensityTilesetUrls().get("forest").get("performance"));
        verify(publicationMapper).updateById(existing);
        InOrder order = inOrder(publicationMapper);
        order.verify(publicationMapper).ensureSceneLock("scene-lock");
        order.verify(publicationMapper).lockScene("scene-lock");
        order.verify(publicationMapper).selectBySceneIdAndVersion("scene-lock", "publication-1");
    }

    @Test
    void rollbackLocksSceneBeforeChangingCurrentPointer() {
        VegetationPublicationDO target = publication("ROLLED_BACK");
        when(publicationMapper.ensureSceneLock("scene-lock")).thenReturn(1);
        when(publicationMapper.lockScene("scene-lock")).thenReturn("scene-lock");
        when(publicationMapper.selectBySceneIdAndVersion("scene-lock", "publication-1"))
                .thenReturn(target);
        when(taskMapper.selectById(7L)).thenReturn(successTask());

        VegetationPublicationService service = new VegetationPublicationService(
                taskMapper, publicationMapper, objectMapper);
        VegetationPublicationResponse response = service.rollback("scene-lock", "publication-1");

        assertEquals("STABLE", response.status());
        verify(publicationMapper).markScenePublicationsRolledBack("scene-lock");
        verify(publicationMapper).updateById(target);
    }

    private VegetationGenerationTaskDO successTask() {
        return VegetationGenerationTaskDO.builder()
                .id(7L)
                .sceneId("scene-lock")
                .status(VegetationTaskStatus.SUCCESS.name())
                .publicationVersion("publication-1")
                .ruleVersion("rule-1")
                .generatorVersion("0.2.1")
                .manifestUrl("/files/manifest.json")
                .tilesetUrl("/files/tileset.json")
                .layerTilesetUrlsJson("{\"forest\":\"/files/forest/tileset.json\"}")
                .layerDensityTilesetUrlsJson("{\"forest\":{"
                        + "\"high\":\"/files/forest/density/high/tileset.json\","
                        + "\"balanced\":\"/files/forest/density/balanced/tileset.json\","
                        + "\"performance\":\"/files/forest/density/performance/tileset.json\"}}")
                .sourceHash("sha256:source")
                .instanceCount(10L)
                .totalTileCount(2)
                .build();
    }

    private VegetationPublicationDO publication(String status) {
        return VegetationPublicationDO.builder()
                .id(8L)
                .sceneId("scene-lock")
                .publicationVersion("publication-1")
                .taskId(7L)
                .ruleVersion("rule-1")
                .generatorVersion("0.2.1")
                .manifestUrl("/files/manifest.json")
                .tilesetUrl("/files/tileset.json")
                .sourceHash("sha256:source")
                .status(status)
                .instanceCount(10L)
                .tileCount(2)
                .build();
    }
}
