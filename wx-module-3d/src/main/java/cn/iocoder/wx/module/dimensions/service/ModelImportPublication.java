package cn.iocoder.wx.module.dimensions.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

import static cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode.*;
import static cn.iocoder.wx.module.dimensions.service.ArtResourceManifestVerifier.*;

/** 发布前从已固定 ZIP 生成普通模型清单，并保存该尝试独有的文件。 */
@Component
class ModelImportPublication {
    private static final String MANIFEST="manifest.json";
    private final ArtResourceObjectStore store;
    private final ModelImportArtifactValidation archive;
    private final ModelResourceManifestVerifier verifier;
    private final ArtResourceManifestVerifier canonical;
    private final String compatibility;
    private final ObjectMapper json=new ObjectMapper();

    ModelImportPublication(ArtResourceObjectStore store,ModelImportArtifactValidation archive,
                           ModelResourceManifestVerifier verifier,ArtResourceManifestVerifier canonical,
                           @Value("${dim3d.model-import.app-compatibility:}") String compatibility) {
        this.store=store;
        this.archive=archive;
        this.verifier=verifier;
        this.canonical=canonical;
        this.compatibility=compatibility;
    }

    boolean configured() {
        return !compatibility.isEmpty() && compatibility.length()<=128 &&
                compatibility.codePoints().noneMatch(c->Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c));
    }

    record Ready(String prefix,String manifestPath,ModelResourceManifestVerifier.Verified manifest) {}

    Ready freeze(String operation,String resourceId,String format,String assetName,String prefix,
                 JsonNode descriptor,BooleanSupplier active) {
        var plan=descriptor.path("validated");
        var manifest=json.createObjectNode().put("schemaVersion","wx.model-resource.v1").put("resourceId",resourceId)
                .put("contentFormat","model."+format+".v1").put("platform","WebGL").put("appCompatibility",compatibility);
        manifest.putObject("entry").put("kind","MODEL").put("assetName",assetName).set("modelPath",plan.path("modelPath"));
        manifest.set("files",plan.path("files"));
        try {
            manifest.put("contentHash",canonical.canonicalHash(manifest));
            byte[] bytes=json.writeValueAsBytes(manifest);
            var checked=verifier.verify(bytes,resourceId,sha256(bytes),MANIFEST);
            return archive.withArchive(operation,format,descriptor,active,file->{
                writeFiles(file,prefix,checked.files(),active);
                try(var input=new ByteArrayInputStream(bytes)) {
                    save(prefix,new FileSpec(MANIFEST,bytes.length,checked.manifestSha256()),input);
                } catch(Exception unavailable){throw new ModelImportArtifactValidation.Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
                return new Ready(prefix,MANIFEST,checked);
            });
        } catch(ModelImportArtifactValidation.Failure | CancellationException ex){throw ex;}
        catch(Exception invalid){throw new ModelImportArtifactValidation.Failure(INVALID_MODEL);}
    }

    private void writeFiles(Path archive,String prefix,List<FileSpec> files,BooleanSupplier active) {
        try(var zip=new ZipFile(archive.toFile())) {
            for(var file:files) {
                ModelImportArtifactVerifier.checkActive(active);
                var entry=zip.getEntry(file.path());
                if(entry==null || entry.getSize()!=file.size() || entry.isDirectory())
                    throw new ModelImportArtifactValidation.Failure(ARTIFACT_UNAVAILABLE);
                try(var input=zip.getInputStream(entry)) {save(prefix,file,input);}
                catch(Exception unavailable){throw new ModelImportArtifactValidation.Failure(ARTIFACT_STORAGE_UNAVAILABLE);}
            }
        } catch(IOException invalid){throw new ModelImportArtifactValidation.Failure(ARTIFACT_UNAVAILABLE);}
    }

    private void save(String prefix,FileSpec file,InputStream input) throws Exception {
        String key=ArtResourceObjectStore.objectKey(prefix,file.path());
        // 尝试前缀由后端独占。若对象已存在，复核其身份并保留原字节。
        if(!store.exists(key))store.write(key,input,file.size());
        store.verifyObject(key,file);
    }
}
