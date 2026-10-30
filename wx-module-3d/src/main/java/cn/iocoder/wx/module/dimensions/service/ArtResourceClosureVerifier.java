package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.*;

/** 将包图、资源目录与灯光绑定到同一已冻结文件集；不在后端伪造Unity原生加载证明。 */
@Component
public class ArtResourceClosureVerifier {
    @Resource private ArtResourceObjectStore store;
    @Resource private ArtResourceManifestVerifier manifest;
    public void verify(String prefix,ArtResourceManifestVerifier.Verified verified){
        Map<String,ArtResourceManifestVerifier.FileSpec> files=new HashMap<>();
        verified.files().forEach(file->files.put(file.path(),file));
        JsonNode packs=manifest.parse(store.readManifest(prefix,verified.entry().get("packManifestPath")));
        JsonNode catalog=manifest.parse(store.readManifest(prefix,verified.entry().get("catalogPath")));
        version(packs);version(catalog);
        if(!"WebGL".equals(text(packs,"platform")) || !"WebGL".equals(text(catalog,"platform"))) fail();
        Map<String,JsonNode> packIndex=index(packs.get("packs"),"packId");
        Map<String,JsonNode> resources=index(catalog.get("resources"),"key");
        Set<String> paths=new HashSet<>();
        store.verifyUnityHeader(prefix,verified.entry().get("nativeManifestPath"));
        for(JsonNode pack:packIndex.values()){
            String file=text(pack,"file");var actual=files.get(file);
            if(actual==null || !paths.add(file) || !hash(text(pack,"sha256")).equals(actual.sha256())
                || !pack.path("size").isIntegralNumber() || pack.path("size").asLong()!=actual.size()
                || !text(pack,"unityHash").matches("[0-9a-fA-F]{32}") || !"WebGL".equals(text(pack,"platform")))fail();
            strings(pack.get("assetNames"));
            store.verifyUnityHeader(prefix,file);
        }
        graph(packIndex);graph(resources);
        JsonNode owner=null;
        for(JsonNode resource:resources.values()){
            JsonNode pack=packIndex.get(text(resource,"packId"));
            if(pack==null || strings(pack.get("assetNames")).stream().noneMatch(name->name.equalsIgnoreCase(text(resource,"assetName"))))fail();
            text(resource,"resourceVersion");
            if(text(resource,"assetName").equals(verified.entry().get("assetName"))
                && text(pack,"file").equals(verified.entry().get("packagePath"))) {
                if(owner!=null)fail();owner=resource;
            }
        }
        if(owner==null || !text(owner,"kind").equalsIgnoreCase(verified.kind().equals("COMPOSITION")?"Model":verified.kind()))fail();
        if(verified.entry().containsKey("lightingManifestPath")){
            JsonNode light=manifest.parse(store.readManifest(prefix,verified.entry().get("lightingManifestPath")));
            lighting(light,owner,resources);
        }else if(resources.values().stream().anyMatch(row->text(row,"kind").equalsIgnoreCase("Lighting")))fail();
    }
    private static void lighting(JsonNode light,JsonNode owner,Map<String,JsonNode> resources){
        version(light);
        if(integer(light.get("rendererSchemaVersion"))!=1 || !text(light,"ownerResourceKey").equals(text(owner,"key"))
            || !text(light,"ownerResourceVersion").equals(text(owner,"resourceVersion"))
            || !"NonDirectional".equals(text(light,"lightmapsMode"))
            || !Set.of("ResourceInternal","FixedPresentation").contains(text(light,"bakeScope")))fail();
        text(light,"lightingVersion");
        JsonNode maps=light.get("lightmaps");array(maps,128);
        Set<Integer> indices=new HashSet<>();
        for(JsonNode map:maps){
            int index=integer(map.get("localIndex"));
            if(index<0 || index>=maps.size() || !indices.add(index))fail();
            JsonNode texture=resources.get(text(map,"colorKey"));
            if(texture==null || !"Texture".equals(text(texture,"kind")))fail();
            for(String key:List.of("directionalKey","shadowMaskKey"))if(map.has(key) && !map.get(key).asText().isEmpty())fail();
        }
        JsonNode renderers=light.get("renderers");array(renderers,16384);
        Set<String> ids=new HashSet<>();
        for(JsonNode row:renderers){
            String id=text(row,"rendererStableId");
            if(!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || !ids.add(id) || !indices.contains(integer(row.get("localLightmapIndex"))))fail();
            numbers(row.get("lightmapScaleOffset"),4,true);
        }
        JsonNode placement=light.get("placement");
        if(placement==null || !"BakedWorld".equals(text(placement,"policy")))fail();
        numbers(placement.get("rootLocalToWorld"),16,false);
        JsonNode placed=placement.get("renderers");array(placed,16384);Set<String> mapped=new HashSet<>();
        for(JsonNode row:placed){if(!mapped.add(text(row,"rendererStableId")))fail();numbers(row.get("localToRoot"),16,false);}
        if(!mapped.equals(ids))fail();
    }
    /** 复用Unity已有包清单的sha256:表示，外层主清单仍使用纯64位小写摘要。 */
    private static String hash(String value){String result=value.startsWith("sha256:")?value.substring(7):value;if(!ArtResourceManifestVerifier.digest(result))fail();return result;}
    private static void graph(Map<String,JsonNode> index){
        Map<String,Integer> remaining=new HashMap<>();Map<String,List<String>> incoming=new HashMap<>();
        for(var row:index.entrySet()){
            Set<String> deps=strings(row.getValue().get("dependencies"));remaining.put(row.getKey(),deps.size());
            for(String dep:deps){if(!index.containsKey(dep))fail();incoming.computeIfAbsent(dep,k->new ArrayList<>()).add(row.getKey());}
        }
        ArrayDeque<String> queue=new ArrayDeque<>();remaining.forEach((key,value)->{if(value==0)queue.add(key);});int count=0;
        while(!queue.isEmpty()){String key=queue.remove();count++;for(String next:incoming.getOrDefault(key,List.of()))if(remaining.compute(next,(k,n)->n-1)==0)queue.add(next);}
        if(count!=index.size())fail();
    }
    private static Map<String,JsonNode> index(JsonNode rows,String key){
        array(rows,4096);Map<String,JsonNode> result=new HashMap<>();
        for(JsonNode row:rows){String id=text(row,key);if(result.putIfAbsent(id,row)!=null)fail();}return result;
    }
    private static Set<String> strings(JsonNode rows){
        if(rows==null || !rows.isArray() || rows.size()>4096)fail();Set<String> result=new HashSet<>();
        for(JsonNode row:rows)if(!row.isTextual() || row.asText().isBlank() || !result.add(row.asText()))fail();return result;
    }
    private static void numbers(JsonNode rows,int length,boolean uv){
        if(rows==null || !rows.isArray() || rows.size()!=length)fail();
        for(int i=0;i<length;i++)if(!rows.get(i).isNumber() || !Double.isFinite(rows.get(i).asDouble()) || Math.abs(rows.get(i).asDouble())>Float.MAX_VALUE || (uv&&i<2&&(float)rows.get(i).asDouble()<=0))fail();
        if(length==16){
            double[] m=new double[16];for(int i=0;i<16;i++)m[i]=rows.get(i).asDouble();
            // 与Unity生产端一致，固定布局必须是可逆仿射变换。
            if(m[3]!=0||m[7]!=0||m[11]!=0||m[15]!=1)fail();
            double determinant=m[0]*(m[5]*m[10]-m[9]*m[6])-m[4]*(m[1]*m[10]-m[9]*m[2])+m[8]*(m[1]*m[6]-m[5]*m[2]);
            if(Math.abs(determinant)<1e-12)fail();
        }
    }
    private static int integer(JsonNode node){if(node==null||!node.isIntegralNumber()||!node.canConvertToInt())fail();return node.asInt();}
    private static void array(JsonNode rows,int max){if(rows==null||!rows.isArray()||rows.isEmpty()||rows.size()>max)fail();}
    private static void version(JsonNode node){if(node==null||!node.path("schemaVersion").isIntegralNumber()||node.path("schemaVersion").asInt()!=1)fail();}
    private static String text(JsonNode node,String key){JsonNode v=node==null?null:node.get(key);if(v==null||!v.isTextual()||v.asText().isBlank())fail();return v.asText();}
    private static void fail(){throw ServiceException.of(INVALID_CLOSURE);}
}
