package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 使用Python合同夹具核对跨语言摘要，不用待测实现生成期望摘要。 */
class ModelResourceManifestVerifierTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ArtResourceManifestVerifier art = new ArtResourceManifestVerifier();
    private final ModelResourceManifestVerifier model = new ModelResourceManifestVerifier();

    private ObjectNode fixture(String format) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while(root != null && !Files.exists(root.resolve("contracts/market01/model-examples.json"))) root=root.getParent();
        assertNotNull(root);
        return (ObjectNode)json.readTree(root.resolve("contracts/market01/model-examples.json").toFile()).get(format);
    }
    private ModelResourceManifestVerifier.Verified verify(ObjectNode root) throws Exception {
        byte[] bytes=json.writeValueAsBytes(root);
        return model.verify(bytes,"101",ArtResourceManifestVerifier.sha256(bytes),"manifest.json");
    }
    @Test void independentFixturesMatchBothFormats() throws Exception {
        for(String format:List.of("gltf","glb")) {
            var root=fixture(format); var checked=verify(root);
            assertEquals(root.get("contentHash").asText(),checked.contentHash());
            assertEquals("model."+format,checked.modelPath());
            assertThrows(UnsupportedOperationException.class,()->checked.files().clear());
            byte[] bytes=json.writeValueAsBytes(root);
            assertThrows(ServiceException.class,()->art.verify(bytes,"101",ArtResourceManifestVerifier.sha256(bytes)));
        }
    }
    @Test void rejectIdentityHashAndWrongBranch() throws Exception {
        var root=fixture("glb");byte[] bytes=json.writeValueAsBytes(root);
        assertThrows(ServiceException.class,()->model.verify(bytes,"102",ArtResourceManifestVerifier.sha256(bytes),"manifest.json"));
        assertThrows(ServiceException.class,()->model.verify(bytes,"101","b".repeat(64),"manifest.json"));
        root.put("contentFormat","unity.assetbundle.v1");
        root.put("contentHash",art.canonicalHash(root));
        assertThrows(ServiceException.class,()->verify(root));
    }
    @Test void rejectUnsafeOrMissingPathsEvenWithValidDigest() throws Exception {
        for(String path:List.of("../model.glb","https://example.invalid/m.glb","missing.glb","model.gltf","model.glb/")) {
            var root=fixture("glb");((ObjectNode)root.get("entry")).put("modelPath",path);
            root.put("contentHash",art.canonicalHash(root));
            assertThrows(ServiceException.class,()->verify(root),path);
        }
    }
    @Test void rejectDuplicateSelfReferenceOversizeAndExtraField() throws Exception {
        for(String mode:List.of("duplicate","self","oversize","extra","overflow","control")) {
            var root=fixture("glb");var file=(ObjectNode)root.get("files").get(0);
            switch(mode) {
                case "duplicate" -> ((com.fasterxml.jackson.databind.node.ArrayNode)root.get("files")).add(file.deepCopy());
                case "self" -> file.put("path","manifest.json");
                case "oversize" -> file.put("size","2147483649");
                case "extra" -> ((ObjectNode)root.get("entry")).put("packagePath","test.ab");
                case "overflow" -> root.put("resourceId","9223372036854775808");
                case "control" -> ((ObjectNode)root.get("entry")).put("assetName","model\nname");
            }
            root.put("contentHash",art.canonicalHash(root));
            assertThrows(ServiceException.class,()->verify(root),mode);
        }
    }
    @Test void strictParsingAndLimitsRemainEnforced() {
        for(String raw:List.of("{} {}","{\"a\":1,\"a\":2}","\ufeff{}")) {
            byte[] bytes=raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThrows(ServiceException.class,()->model.verify(bytes,"101",ArtResourceManifestVerifier.sha256(bytes),"manifest.json"));
        }
        assertThrows(ServiceException.class,()->model.verify(new byte[ArtResourceManifestVerifier.MAX_MANIFEST_BYTES+1],"101","a".repeat(64),"manifest.json"));
    }
}
