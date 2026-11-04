package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/** 使用真实压缩文件与二进制模型头验证模型依赖边界。 */
class ModelImportArtifactVerifierTest {
    @TempDir Path temp;
    private final ModelImportArtifactVerifier verifier=new ModelImportArtifactVerifier();

    @Test void verifiesNestedModelAndReturnsSortedByteIdentities() throws IOException {
        byte[] model=json("""
                "buffers":[{"uri":"mesh.bin","byteLength":4}],
                "bufferViews":[{"buffer":0,"byteOffset":1,"byteLength":3}],
                "images":[{"uri":"texture.png"}]
                """);
        byte[] bin={1,2,3,4};
        var checked=verifier.verify(zip(Map.of("dir/scene.gltf",model,"dir/mesh.bin",bin,"dir/texture.png",new byte[]{8})),"gltf",()->true);
        assertEquals("dir/scene.gltf",checked.modelPath());
        assertEquals(List.of("dir/mesh.bin","dir/scene.gltf","dir/texture.png"),checked.files().stream().map(ArtResourceManifestVerifier.FileSpec::path).toList());
        assertEquals(ArtResourceManifestVerifier.sha256(bin),checked.files().get(0).sha256());
    }

    @Test void glbChecksHeaderChunkAndEmbeddedBufferBounds() throws IOException {
        byte[] model=json("""
                "buffers":[{"byteLength":3}],
                "bufferViews":[{"buffer":0,"byteLength":3}],
                "images":[{"bufferView":0,"mimeType":"image/png"}]
                """);
        var bytes=glb(model,4);
        assertEquals("scene.glb",verifier.verify(zip(Map.of("scene.glb",bytes)),"glb",()->true).modelPath());
        bytes[8]=0;
        assertThrows(ServiceException.class,()->verifier.verify(zip(Map.of("scene.glb",bytes)),"glb",()->true));
        assertThrows(ServiceException.class,()->verifier.verify(zip(Map.of("scene.glb",glb(model,8))),"glb",()->true));
    }

    @Test void rejectsMissingRemoteEncodedAndTraversalReferences() throws IOException {
        for(String ref:List.of("missing.bin","https://assets.invalid/mesh.bin","//host/mesh.bin","../mesh.bin","%2e%2e/mesh.bin","mesh.bin?download=1")){
            var entries=Map.of("dir/scene.gltf",json("\"buffers\":[{\"uri\":\""+ref+"\",\"byteLength\":4}]"),"dir/mesh.bin",new byte[]{1,2,3,4});
            assertThrows(ServiceException.class,()->verifier.verify(zip(entries),"gltf",()->true),ref);
        }
    }

    @Test void rejectsShortBufferBadViewAndUnknownExtensionUri() throws IOException {
        for(String input:List.of(
                "\"buffers\":[{\"uri\":\"mesh.bin\",\"byteLength\":5}]",
                "\"buffers\":[{\"uri\":\"mesh.bin\",\"byteLength\":4}],\"bufferViews\":[{\"buffer\":0,\"byteOffset\":3,\"byteLength\":2}]",
                "\"buffers\":[{\"uri\":\"mesh.bin\",\"byteLength\":4}],\"extensions\":{\"TEST_extension\":{\"uri\":\"https://host/remote\"}}")){
            assertThrows(ServiceException.class,()->verifier.verify(zip(Map.of("scene.gltf",json(input),"mesh.bin",new byte[]{1,2,3,4})),"gltf",()->true));
        }
    }

    @Test void acceptsInlineBase64AndRejectsInvalidInlineBuffer() throws IOException {
        byte[] valid=json("\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,AQIDBA==\",\"byteLength\":4}]");
        assertEquals(1,verifier.verify(zip(Map.of("scene.gltf",valid)),"gltf",()->true).files().size());
        byte[] invalid=json("\"buffers\":[{\"uri\":\"data:application/octet-stream;base64,AQIDBA==\",\"byteLength\":5}]");
        assertThrows(ServiceException.class,()->verifier.verify(zip(Map.of("scene.gltf",invalid)),"gltf",()->true));
    }

    @Test void rejectsUnexpectedFilesPathCollisionsAndMissingDirectoryRecord() throws IOException {
        byte[] model=json("\"scene\":0,\"scenes\":[{}]");
        for(var entries:List.of(Map.of("scene.gltf",model,"source.zip",new byte[]{1}),
                Map.of("scene.gltf",model,"SCENE.GLTF",model),Map.of("../scene.gltf",model),
                Map.of("scene.gltf",model,"other.glb",new byte[]{1}))) {
            assertThrows(ServiceException.class,()->verifier.verify(zip(entries),"gltf",()->true));
        }
        var path=zip(Map.of("scene.gltf",model));
        byte[] complete=Files.readAllBytes(path);
        Files.write(path,Arrays.copyOf(complete,complete.length-22));
        assertThrows(ServiceException.class,()->verifier.verify(path,"gltf",()->true));
    }

    static byte[] json(String fields) {
        return ("{\"asset\":{\"version\":\"2.0\"},"+fields+"}").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] glb(byte[] json,int binaryLength) {
        int padded=(json.length+3)&~3;
        ByteBuffer bytes=ByteBuffer.allocate(28+padded+binaryLength).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(0x46546c67).putInt(2).putInt(bytes.capacity()).putInt(padded).putInt(0x4e4f534a).put(json);
        while(bytes.position()<20+padded)bytes.put((byte)' ');
        bytes.putInt(binaryLength).putInt(0x004e4942);
        return bytes.array();
    }

    private Path zip(Map<String,byte[]> entries) throws IOException {
        Path path=Files.createTempFile(temp,"model-",".zip");
        try(var output=new ZipOutputStream(Files.newOutputStream(path))) {
            for(var entry:entries.entrySet()){
                output.putNextEntry(new ZipEntry(entry.getKey()));output.write(entry.getValue());output.closeEntry();
            }
        }
        return path;
    }
}
