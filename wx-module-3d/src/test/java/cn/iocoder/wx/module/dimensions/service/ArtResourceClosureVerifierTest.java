package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 依赖、包索引和固定烘焙关联校验，不冒充Unity原生加载测试。 */
class ArtResourceClosureVerifierTest {
    ObjectMapper json=new ObjectMapper();ArtResourceClosureVerifier closure;ArtResourceObjectStore store;
    ObjectNode packs,catalog,light;boolean lighting;
    @BeforeEach void setup()throws Exception{
        store=mock(ArtResourceObjectStore.class);closure=new ArtResourceClosureVerifier();ReflectionTestUtils.setField(closure,"store",store);ReflectionTestUtils.setField(closure,"manifest",new ArtResourceManifestVerifier());
        packs=(ObjectNode)json.readTree("{\"schemaVersion\":1,\"platform\":\"WebGL\",\"packs\":[{\"packId\":\"car\",\"file\":\"car.bundle\",\"sha256\":\""+"a".repeat(64)+"\",\"size\":10,\"unityHash\":\""+"0".repeat(32)+"\",\"platform\":\"WebGL\",\"dependencies\":[],\"assetNames\":[\"car\",\"lightmap\"]}]}");
        catalog=(ObjectNode)json.readTree("{\"schemaVersion\":1,\"platform\":\"WebGL\",\"resources\":[{\"key\":\"model/car\",\"kind\":\"Model\",\"packId\":\"car\",\"assetName\":\"car\",\"resourceVersion\":\"v1\",\"dependencies\":[]},{\"key\":\"texture/light\",\"kind\":\"Texture\",\"packId\":\"car\",\"assetName\":\"lightmap\",\"resourceVersion\":\"v1\",\"dependencies\":[]}]}");
        light=(ObjectNode)json.readTree("{\"schemaVersion\":1,\"rendererSchemaVersion\":1,\"ownerResourceKey\":\"model/car\",\"ownerResourceVersion\":\"v1\",\"lightingVersion\":\"l1\",\"bakeScope\":\"ResourceInternal\",\"lightmapsMode\":\"NonDirectional\",\"lightmaps\":[{\"localIndex\":0,\"colorKey\":\"texture/light\",\"directionalKey\":\"\",\"shadowMaskKey\":\"\"}],\"renderers\":[{\"rendererStableId\":\"renderer-1\",\"localLightmapIndex\":0,\"lightmapScaleOffset\":[1,1,0,0]}],\"placement\":{\"policy\":\"BakedWorld\",\"rootLocalToWorld\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1],\"renderers\":[{\"rendererStableId\":\"renderer-1\",\"localToRoot\":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}]}}");
    }
    void verify()throws Exception{
        when(store.readManifest("resources/frozen/","packs.json")).thenReturn(json.writeValueAsBytes(packs));when(store.readManifest("resources/frozen/","catalog.json")).thenReturn(json.writeValueAsBytes(catalog));when(store.readManifest("resources/frozen/","lighting.json")).thenReturn(json.writeValueAsBytes(light));
        Map<String,String> entry=new HashMap<>(Map.of("packagePath","car.bundle","assetName","car","nativeManifestPath","native","catalogPath","catalog.json","packManifestPath","packs.json"));if(lighting)entry.put("lightingManifestPath","lighting.json");
        closure.verify("resources/frozen/",new ArtResourceManifestVerifier.Verified("1","a".repeat(64),"b".repeat(64),"test","MODEL",entry,List.of(new ArtResourceManifestVerifier.FileSpec("car.bundle",10,"a".repeat(64))),10));
    }
    @Test void plainModelPasses()throws Exception{verify();}
    @Test void existingUnityHashPrefixAndAssetCaseAreCompatible()throws Exception{((ObjectNode)packs.get("packs").get(0)).put("sha256","sha256:"+"a".repeat(64));((ArrayNode)packs.get("packs").get(0).get("assetNames")).set(0,json.getNodeFactory().textNode("CAR"));verify();}
    @Test void realLinkedNonDirectionalLayoutPasses()throws Exception{lighting=true;verify();}
    @Test void missingPackDependencyRejected(){((ArrayNode)packs.get("packs").get(0).get("dependencies")).add("missing");assertThrows(ServiceException.class,()->verify());}
    @Test void packageCycleRejected(){((ArrayNode)packs.get("packs").get(0).get("dependencies")).add("car");assertThrows(ServiceException.class,()->verify());}
    @Test void resourceCycleRejected(){((ArrayNode)catalog.get("resources").get(0).get("dependencies")).add("model/car");assertThrows(ServiceException.class,()->verify());}
    @Test void stalePackageHashRejected(){((ObjectNode)packs.get("packs").get(0)).put("sha256","b".repeat(64));assertThrows(ServiceException.class,()->verify());}
    @Test void assetNotInPackageRejected(){((ObjectNode)catalog.get("resources").get(0)).put("assetName","missing");assertThrows(ServiceException.class,()->verify());}
    @Test void lightingWrongModelRejected(){lighting=true;light.put("ownerResourceVersion","v2");assertThrows(ServiceException.class,()->verify());}
    @Test void shadowmaskRejected(){lighting=true;((ObjectNode)light.get("lightmaps").get(0)).put("shadowMaskKey","texture/light");assertThrows(ServiceException.class,()->verify());}
    @Test void directionalRejected(){lighting=true;light.put("lightmapsMode","CombinedDirectional");assertThrows(ServiceException.class,()->verify());}
    @Test void missingTextureRejected(){lighting=true;((ObjectNode)light.get("lightmaps").get(0)).put("colorKey","texture/missing");assertThrows(ServiceException.class,()->verify());}
    @Test void wrongPlacementRejected(){lighting=true;((ObjectNode)light.get("placement")).put("policy","Arbitrary");assertThrows(ServiceException.class,()->verify());}
    @Test void rendererSetMismatchRejected(){lighting=true;((ObjectNode)light.get("placement").get("renderers").get(0)).put("rendererStableId","other");assertThrows(ServiceException.class,()->verify());}
    @Test void singularLayoutRejected(){lighting=true;((ArrayNode)light.get("placement").get("rootLocalToWorld")).set(0,json.getNodeFactory().numberNode(0));assertThrows(ServiceException.class,()->verify());}
    @Test void nonAffineLayoutRejected(){lighting=true;((ArrayNode)light.get("placement").get("rootLocalToWorld")).set(15,json.getNodeFactory().numberNode(2));assertThrows(ServiceException.class,()->verify());}
    @Test void numericStringSchemaRejected(){lighting=true;light.put("rendererSchemaVersion","1");assertThrows(ServiceException.class,()->verify());}
    @Test void invalidUvScaleRejected(){lighting=true;((ArrayNode)light.get("renderers").get(0).get("lightmapScaleOffset")).set(0,json.getNodeFactory().numberNode(0));assertThrows(ServiceException.class,()->verify());}
}
