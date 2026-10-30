import cn.iocoder.wx.module.dimensions.service.ArtResourceObjectStore;
import cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier;
import cn.iocoder.wx.module.dimensions.config.MinioConfigInfo;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import io.minio.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 独立调用新存储模块验收，不启动Spring、不注册路由、不改数据库或旧资源。 */
class Art03StorageProbe {
    static void inject(Object target,String name,Object value)throws Exception{
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static void main(String[] args)throws Exception{
        Path root=Path.of("/opt/wx/editor-test148");
        Map<String,String> env=new HashMap<>();
        for(String line:Files.readAllLines(root.resolve(".env")))if(!line.isBlank()&&!line.startsWith("#")){int at=line.indexOf('=');if(at>0)env.put(line.substring(0,at),line.substring(at+1));}
        MinioClient client=MinioClient.builder().endpoint("http://127.0.0.1:9000").credentials(env.get("WX_MINIO_ACCESS_KEY"),env.get("WX_MINIO_SECRET_KEY")).build();
        MinioConfigInfo config=new MinioConfigInfo();config.setBucketName("wx-art-test148");
        ArtResourceObjectStore store=new ArtResourceObjectStore();inject(store,"minio",client);inject(store,"config",config);
        String prefix="resources/art03-storage-probe/"+UUID.randomUUID()+"/";
        String stage=prefix+"stage/",frozen=prefix+"frozen/";
        String path="probe.bin";
        byte[] content="ART03真实对象冻结验收".getBytes(StandardCharsets.UTF_8);
        var file=new ArtResourceManifestVerifier.FileSpec(path,content.length,ArtResourceManifestVerifier.sha256(content));
        int checks=0;
        try{
            client.putObject(PutObjectArgs.builder().bucket(config.getBucketName()).object(stage+path).stream(new ByteArrayInputStream(content),content.length,-1).build());
            store.verifyObject(stage+path,file);checks++;
            store.freeze(stage,frozen,List.of(file));store.verifyObject(frozen+path,file);checks++;
            byte[] changed=content.clone();changed[0]^=1;
            client.putObject(PutObjectArgs.builder().bucket(config.getBucketName()).object(stage+path).stream(new ByteArrayInputStream(changed),changed.length,-1).build());
            store.verifyObject(frozen+path,file);checks++;
            boolean badRejected=false;
            try{store.freeze(stage,prefix+"rejected/",List.of(file));}catch(ServiceException expected){badRejected=true;}
            if(!badRejected)throw new AssertionError("暂存摘要不一致必须拒绝");checks++;
            client.putObject(PutObjectArgs.builder().bucket(config.getBucketName()).object(stage+path).stream(new ByteArrayInputStream(content),content.length,-1).build());
            boolean overwriteRejected=false;
            try{store.freeze(stage,frozen,List.of(file));}catch(ServiceException expected){overwriteRejected=true;}
            if(!overwriteRejected)throw new AssertionError("已存在冻结对象必须拒绝覆盖");checks++;
        }finally{
            for(String key:List.of(stage+path,frozen+path,prefix+"rejected/"+path))client.removeObject(RemoveObjectArgs.builder().bucket(config.getBucketName()).object(key).build());
        }
        String report="{\"passed\":true,\"checks\":"+checks+",\"cleanup\":true,\"databaseChanged\":false,\"apiEnabled\":false}";
        Files.writeString(root.resolve("art03-storage-probe.json"),report,StandardCharsets.UTF_8);System.out.println(report);
    }
}
