package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.INVALID_CLOSURE;
import static cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier.*;

/** 校验转换 ZIP 的文件身份及普通模型依赖边界，不解压到目录。 */
@Component
class ModelImportArtifactVerifier {
    private static final int MAX_CENTRAL_BYTES=4*1024*1024;
    private static final int GLB_MAGIC=0x46546c67;
    private static final int JSON_CHUNK=0x4e4f534a;
    private static final int BIN_CHUNK=0x004e4942;
    private static final Set<String> BUFFER_DATA_TYPES=Set.of("application/octet-stream","application/gltf-buffer");
    private static final Set<String> IMAGE_TYPES=Set.of("image/png","image/jpeg","image/webp","image/ktx2");
    private final ArtResourceManifestVerifier json=new ArtResourceManifestVerifier();

    record Verified(String modelPath,List<FileSpec> files) {}
    private record Model(JsonNode root,long binaryLength) {}

    Verified verify(Path path,String format,BooleanSupplier active) {
        require("gltf".equals(format) || "glb".equals(format));
        try {
            int expectedCount=centralDirectoryCount(path);
            try(var zip=new ZipFile(path.toFile())) {
                require(zip.size()==expectedCount);
                return verifyZip(zip,format,active);
            }
        } catch(IOException | IllegalArgumentException invalid) {
            throw ServiceException.of(INVALID_CLOSURE);
        }
    }

    // 先界定中央目录大小，避免 ZipFile 为恶意条目数量分配无界内存。
    private static int centralDirectoryCount(Path path) throws IOException {
        try(var file=new RandomAccessFile(path.toFile(),"r")) {
            long length=file.length();
            require(length>=22);
            byte[] tail=new byte[(int)Math.min(length,22+65535+20)];
            file.seek(length-tail.length);
            file.readFully(tail);
            ByteBuffer directory=ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
            for(int offset=tail.length-22;offset>=0;offset--) {
                if(directory.getInt(offset)!=0x06054b50 ||
                        offset+22+Short.toUnsignedInt(directory.getShort(offset+20))!=tail.length) continue;
                int count=Short.toUnsignedInt(directory.getShort(offset+10));
                long size=Integer.toUnsignedLong(directory.getInt(offset+12));
                long start=Integer.toUnsignedLong(directory.getInt(offset+16));
                require((offset<20 || directory.getInt(offset-20)!=0x07064b50) && directory.getInt(offset+4)==0 && Short.toUnsignedInt(directory.getShort(offset+8))==count);
                require(count>0 && count<=MAX_FILES && size>=46L*count && size<=MAX_CENTRAL_BYTES &&
                        start+size==length-tail.length+offset);
                return count;
            }
        }
        throw ServiceException.of(INVALID_CLOSURE);
    }

    private Verified verifyZip(ZipFile zip,String format,BooleanSupplier active) throws IOException {
        TreeMap<String,FileSpec> files=new TreeMap<>();
        Set<String> paths=new HashSet<>();
        Set<String> foldedFiles=new HashSet<>();
        ZipEntry model=null;
        long total=0;
        byte[] buffer=new byte[64*1024];
        for(var entries=zip.entries();entries.hasMoreElements();) {
            checkActive(active);
            ZipEntry entry=entries.nextElement();
            String path=entry.getName();
            if(entry.isDirectory())path=path.substring(0,path.length()-1);
            requirePath(path);
            String folded=path.toLowerCase(Locale.ROOT);
            require(paths.add(folded));
            long size=entry.getSize();
            if(entry.isDirectory()) {
                require(size==0);
                continue;
            }
            total+=size;
            require(size>0 && size<=MAX_FILE_BYTES && total<=MAX_CLOSURE_BYTES);
            if(folded.endsWith(".gltf") || folded.endsWith(".glb")) {
                require(model==null && path.endsWith("."+format));
                model=entry;
            }
            foldedFiles.add(folded);
            files.put(path,new FileSpec(path,size,hash(zip,entry,buffer,active)));
        }
        // 大小写折叠后拒绝文件占用父目录，保持不同平台的一致解析。
        for(String path:paths) {
            for(int slash=path.indexOf('/');slash>=0;slash=path.indexOf('/',slash+1)) {
                require(!foldedFiles.contains(path.substring(0,slash)));
            }
        }
        require(model!=null);
        Model decoded=readModel(zip,model,format);
        verifyReferences(decoded,model.getName(),files);
        return new Verified(model.getName(),List.copyOf(files.values()));
    }

    private static String hash(ZipFile zip,ZipEntry entry,byte[] buffer,BooleanSupplier active) throws IOException {
        var sha=sha256Digest();
        var crc=new CRC32();
        long size=0;
        try(var input=zip.getInputStream(entry)) {
            for(int read;(read=input.read(buffer))!=-1;) {
                checkActive(active);
                size+=read;
                require(size<=entry.getSize());
                sha.update(buffer,0,read);
                crc.update(buffer,0,read);
            }
        }
        require(size==entry.getSize() && crc.getValue()==entry.getCrc());
        return HexFormat.of().formatHex(sha.digest());
    }

    private Model readModel(ZipFile zip,ZipEntry entry,String format) throws IOException {
        try(var input=new DataInputStream(new BufferedInputStream(zip.getInputStream(entry)))) {
            long size=entry.getSize();
            if("gltf".equals(format)) {
                require(size<=MAX_MANIFEST_BYTES);
                return new Model(json.parse(input.readNBytes((int)size)),-1);
            }
            require(size>=20 && littleInt(input)==GLB_MAGIC && littleInt(input)==2 && littleUnsigned(input)==size);
            long jsonLength=littleUnsigned(input);
            require(jsonLength>0 && jsonLength<=MAX_MANIFEST_BYTES && jsonLength%4==0 && jsonLength<=size-20 &&
                    littleInt(input)==JSON_CHUNK);
            JsonNode root=json.parse(input.readNBytes((int)jsonLength));
            long remaining=size-20-jsonLength;
            if(remaining==0)return new Model(root,-1);
            require(remaining>=8);
            long binaryLength=littleUnsigned(input);
            // 本期转换器仅产出标准 JSON/BIN 块，不接收其他容器扩展块。
            require(binaryLength>0 && binaryLength%4==0 && binaryLength==remaining-8 && littleInt(input)==BIN_CHUNK);
            return new Model(root,binaryLength);
        }
    }

    private static void verifyReferences(Model model,String modelPath,Map<String,FileSpec> files) {
        JsonNode root=model.root();
        require(root!=null);
        JsonNode asset=root.path("asset");
        require(root.isObject() && "2.0".equals(asset.path("version").textValue()) &&
                (!asset.has("minVersion") || "2.0".equals(asset.path("minVersion").textValue())));
        int slash=modelPath.lastIndexOf('/');
        String parent=slash<0?"":modelPath.substring(0,slash+1);
        Set<String> used=new HashSet<>();
        used.add(modelPath);
        Set<JsonNode> checkedUris=Collections.newSetFromMap(new IdentityHashMap<>());
        JsonNode buffers=array(root,"buffers");
        long[] bufferLengths=new long[buffers.size()];
        boolean binaryUsed=false;
        for(int i=0;i<buffers.size();i++) {
            JsonNode buffer=buffers.get(i);
            long length=positive(buffer.path("byteLength"));
            bufferLengths[i]=length;
            if(buffer.has("uri")) {
                require(uriSize(buffer,parent,files,used,checkedUris,BUFFER_DATA_TYPES)==length);
            } else {
                require(i==0 && model.binaryLength()>=length && model.binaryLength()-length<=3);
                binaryUsed=true;
            }
        }
        require(binaryUsed==(model.binaryLength()>=0));
        JsonNode views=array(root,"bufferViews");
        for(JsonNode view:views) {
            int index=index(view.path("buffer"),bufferLengths.length);
            long offset=view.has("byteOffset")?nonNegative(view.path("byteOffset")):0;
            long length=positive(view.path("byteLength"));
            require(offset<=bufferLengths[index] && length<=bufferLengths[index]-offset);
        }
        for(JsonNode image:array(root,"images")) {
            require(image.has("uri")!=image.has("bufferView"));
            if(image.has("uri")) {
                uriSize(image,parent,files,used,checkedUris,IMAGE_TYPES);
            } else {
                index(image.path("bufferView"),views.size());
                require(IMAGE_TYPES.contains(image.path("mimeType").asText("")));
            }
        }
        rejectUnhandledUris(root,checkedUris);
        require(used.size()==files.size());
    }

    private static long uriSize(JsonNode owner,String parent,Map<String,FileSpec> files,Set<String> used,
                                Set<JsonNode> checkedUris,Set<String> dataTypes) {
        String uri=owner.path("uri").textValue();
        require(uri!=null && !uri.isEmpty());
        checkedUris.add(owner);
        if(uri.startsWith("data:")) {
            int comma=uri.indexOf(',');
            require(comma>12 && comma<64 && uri.substring(0,comma).endsWith(";base64") &&
                    dataTypes.contains(uri.substring(5,comma-7)));
            int size=Base64.getDecoder().decode(uri.substring(comma+1)).length;
            require(size>0);
            return size;
        }
        requirePath(uri);
        String path=parent+uri;
        FileSpec file=files.get(path);
        require(file!=null && !path.endsWith(".gltf") && !path.endsWith(".glb"));
        used.add(path);
        return file.size();
    }

    // 未支持的扩展资源不能跳过核心缓冲及图片引用门禁。
    private static void rejectUnhandledUris(JsonNode node,Set<JsonNode> checkedUris) {
        require(!node.has("uri") || checkedUris.contains(node));
        if(node.isContainerNode())for(JsonNode child:node)rejectUnhandledUris(child,checkedUris);
    }

    private static JsonNode array(JsonNode parent,String name) {
        JsonNode value=parent.path(name);
        if(value.isMissingNode())return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        require(value.isArray());
        return value;
    }
    private static int index(JsonNode node,int limit) {
        long value=nonNegative(node);
        require(value<limit);
        return (int)value;
    }
    private static long positive(JsonNode node) {
        long value=nonNegative(node);
        require(value>0);
        return value;
    }
    private static long nonNegative(JsonNode node) {
        require(node.isIntegralNumber() && node.canConvertToLong() && node.longValue()>=0);
        return node.longValue();
    }
    private static int littleInt(DataInputStream input) throws IOException {return Integer.reverseBytes(input.readInt());}
    private static long littleUnsigned(DataInputStream input) throws IOException {return Integer.toUnsignedLong(littleInt(input));}
    private static void require(boolean condition) {
        if(!condition)throw ServiceException.of(INVALID_CLOSURE);
    }
    static void checkActive(BooleanSupplier active) {
        if(!active.getAsBoolean())throw new CancellationException();
    }
    static MessageDigest sha256Digest() {
        try {return MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
