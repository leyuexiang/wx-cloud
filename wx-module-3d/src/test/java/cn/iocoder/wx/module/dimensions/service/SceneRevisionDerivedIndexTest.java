package cn.iocoder.wx.module.dimensions.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DATA-06 同书签派生索引的结构门禁。 */
class SceneRevisionDerivedIndexTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String HASH = "a".repeat(64);

    @Test
    void buildsStableInstanceAndChunkIndex() throws Exception {
        var threeD = json.readTree("""
                {
                  "objects":[
                    {"instanceId":"root","sortNo":0,"layerKey":"layer-a","resourceRefKeys":["asset-a"]},
                    {"instanceId":"child","parentInstanceId":"root","sortNo":1,"resourceRefKeys":[]}
                  ],
                  "components":[],
                  "streaming":{"chunks":[{"id":"chunk-a","objectIds":["root","child"]}]}
                }
                """);

        var index = SceneRevisionDerivedIndex.build(2, "scene-1", 3, HASH, threeD);

        assertEquals(1, index.indexVersion());
        assertEquals(List.of("root", "child"), index.chunks().get(0).instanceIds());
        assertEquals("root", index.instances().get(1).parentInstanceId());
        assertEquals("asset-a", index.instances().get(0).resourceRefKeys().get(0));
    }

    @Test
    void rejectsParentCycle() throws Exception {
        var threeD = json.readTree("""
                {"objects":[
                  {"instanceId":"a","parentInstanceId":"b"},
                  {"instanceId":"b","parentInstanceId":"a"}
                ]}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> SceneRevisionDerivedIndex.build(2, "scene-1", 1, HASH, threeD));
    }

    @Test
    void rejectsUnknownOrDuplicateChunkMembers() throws Exception {
        var unknown = json.readTree("""
                {"objects":[{"instanceId":"root"}],"streaming":{"chunks":[{"id":"c","objectIds":["missing"]}]}}
                """);
        var duplicate = json.readTree("""
                {"objects":[{"instanceId":"root"}],"streaming":{"chunks":[{"id":"c","objectIds":["root","root"]}]}}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> SceneRevisionDerivedIndex.build(2, "scene-1", 1, HASH, unknown));
        assertThrows(IllegalArgumentException.class,
                () -> SceneRevisionDerivedIndex.build(2, "scene-1", 1, HASH, duplicate));
    }
}
