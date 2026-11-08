package cn.iocoder.wx.module.dimensions.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 使用本地修订样本验证正文摘要边界和可测量的去重收益。 */
class SceneRevisionContentHasherTest {
    @Test
    void samePackagesProduceStableDigestAndByteCount() {
        String scene = "{\"sceneType\":\"gis\",\"name\":\"园区\"}";
        String threeD = "{\"nodes\":[{\"id\":\"root\"}]}";
        String twoD = "{\"layers\":[]}";
        String dependencies = "{\"resources\":[]}";

        assertEquals(SceneRevisionContentHasher.sha256(1, scene, threeD, twoD, dependencies),
                SceneRevisionContentHasher.sha256(1, scene, threeD, twoD, dependencies));
        assertEquals(scene.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + threeD.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + twoD.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        + dependencies.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                SceneRevisionContentHasher.contentBytes(scene, threeD, twoD, dependencies));
    }

    @Test
    void packageBoundaryAndSchemaVersionArePartOfIdentity() {
        String first = SceneRevisionContentHasher.sha256(1, "ab", "c", "d", "e");
        String boundaryChanged = SceneRevisionContentHasher.sha256(1, "a", "bc", "d", "e");
        String schemaChanged = SceneRevisionContentHasher.sha256(2, "ab", "c", "d", "e");

        assertNotEquals(first, boundaryChanged);
        assertNotEquals(first, schemaChanged);
    }

    @Test
    void repeatedRevisionBodiesShareOneContentRowInLocalSample() {
        List<String> hashes = new ArrayList<>();
        long rawBytes = 0;
        for (int revision = 1; revision <= 20; revision++) {
            int checkpoint = (revision - 1) / 5;
            String scene = "{\"sceneType\":\"gis\",\"name\":\"园区\",\"checkpoint\":" + checkpoint + "}";
            String threeD = "{\"nodes\":[{\"id\":\"root\",\"revision\":" + checkpoint + "}]}";
            String twoD = "{\"layers\":[]}";
            String dependencies = "{\"resources\":[]}";
            hashes.add(SceneRevisionContentHasher.sha256(1, scene, threeD, twoD, dependencies));
            rawBytes += SceneRevisionContentHasher.contentBytes(scene, threeD, twoD, dependencies);
        }
        Set<String> uniqueHashes = new HashSet<>(hashes);
        long uniqueBytes = 0;
        for (int checkpoint = 0; checkpoint < 4; checkpoint++) {
            String scene = "{\"sceneType\":\"gis\",\"name\":\"园区\",\"checkpoint\":" + checkpoint + "}";
            String threeD = "{\"nodes\":[{\"id\":\"root\",\"revision\":" + checkpoint + "}]}";
            uniqueBytes += SceneRevisionContentHasher.contentBytes(scene, threeD, "{\"layers\":[]}", "{\"resources\":[]}");
        }

        assertEquals(4, uniqueHashes.size());
        assertTrue(uniqueBytes < rawBytes);
    }

    @Test
    void nullPackageAndInvalidSchemaAreRejected() {
        assertThrows(NullPointerException.class, () -> SceneRevisionContentHasher.sha256(1, null, "{}", "{}", "{}"));
        assertThrows(IllegalArgumentException.class, () -> SceneRevisionContentHasher.sha256(0, "{}", "{}", "{}", "{}"));
    }
}
