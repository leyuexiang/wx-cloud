package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier.*;

/** 普通模型清单独立分支；不改变现有Unity热更清单和提交入口的接受范围。 */
@Service
public class ModelResourceManifestVerifier {
    private static final Set<String> ROOT = Set.of("schemaVersion","resourceId","contentHash","contentFormat","platform","appCompatibility","entry","files");
    private final ArtResourceManifestVerifier common = new ArtResourceManifestVerifier();

    public record Verified(String resourceId, String manifestSha256, String contentHash, String contentFormat,
                           String compatibility, String modelPath, List<FileSpec> files, long totalBytes) {}

    public Verified verify(byte[] bytes, String expectedId, String expectedManifestHash, String manifestPath) {
        requirePath(manifestPath);
        if(bytes==null || bytes.length==0 || bytes.length>MAX_MANIFEST_BYTES) fail();
        String manifestHash=sha256(bytes);
        if(!digest(expectedManifestHash) || !manifestHash.equals(expectedManifestHash))
            throw ServiceException.of(ArtResourceErrorCode.CHECKSUM_MISMATCH);
        JsonNode root=common.parse(bytes);fields(root,ROOT);
        if(!"wx.model-resource.v1".equals(text(root,"schemaVersion",32)) || !"WebGL".equals(text(root,"platform",20))) fail();
        String id=text(root,"resourceId",19);
        if(!id.matches("[1-9][0-9]{0,18}") || !id.equals(expectedId)) fail();
        try { if(Long.parseLong(id)<=0) fail(); } catch(NumberFormatException ex) { fail(); }
        String format=text(root,"contentFormat",32);
        if(!Set.of("model.gltf.v1","model.glb.v1").contains(format)) fail();
        String compatibility=text(root,"appCompatibility",128);
        if(compatibility.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) fail();
        String hash=text(root,"contentHash",64);if(!digest(hash)) fail();
        JsonNode entry=root.get("entry");fields(entry,Set.of("kind","assetName","modelPath"));
        if(!"MODEL".equals(text(entry,"kind",20))) fail();
        text(entry,"assetName",150);
        String modelPath=text(entry,"modelPath",512);requirePath(modelPath);
        if(!modelPath.endsWith(format.equals("model.glb.v1")?".glb":".gltf")) fail();
        JsonNode array=root.get("files");
        if(!array.isArray() || array.isEmpty() || array.size()>MAX_FILES) fail();
        Map<String,FileSpec> files=new TreeMap<>();long total=0;
        for(JsonNode file:array) {
            fields(file,Set.of("path","size","sha256"));
            String path=text(file,"path",512);requirePath(path);
            if(path.equals(manifestPath)) fail();
            String sizeText=text(file,"size",10);
            if(!sizeText.matches("[1-9][0-9]{0,9}")) fail();
            long size=Long.parseLong(sizeText);total+=size;
            if(size>MAX_FILE_BYTES || total>MAX_CLOSURE_BYTES) fail();
            String fileHash=text(file,"sha256",64);
            if(!digest(fileHash) || files.putIfAbsent(path,new FileSpec(path,size,fileHash))!=null) fail();
        }
        if(!files.containsKey(modelPath)) fail();
        // 先核验完整结构再复用摘要，避免宽松文本转换掩盖字段类型错误。
        if(!common.canonicalHash(root).equals(hash)) throw ServiceException.of(ArtResourceErrorCode.CHECKSUM_MISMATCH);
        return new Verified(id,manifestHash,hash,format,compatibility,modelPath,List.copyOf(files.values()),total);
    }

    private static void fields(JsonNode node, Set<String> required) {
        if(node==null || !node.isObject() || node.size()!=required.size()) fail();
        for(String key:required) if(!node.has(key)) fail();
    }
    private static String text(JsonNode node,String key,int limit) {
        JsonNode value=node.get(key);
        if(value==null || !value.isTextual() || value.asText().isBlank() || value.asText().length()>limit) fail();
        String text=value.asText();
        // JSON转义也可生成孤立代理码元，必须显式拒绝，保持Java/Python摘要一致。
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c<32 || c==127) fail();
            if(Character.isHighSurrogate(c)) {
                if(++i>=text.length() || !Character.isLowSurrogate(text.charAt(i))) fail();
            } else if(Character.isLowSurrogate(c)) fail();
        }
        return text;
    }
    private static void fail() { throw ServiceException.of(ArtResourceErrorCode.INVALID_CLOSURE); }
}
