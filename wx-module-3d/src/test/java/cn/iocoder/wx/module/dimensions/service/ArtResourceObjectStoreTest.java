package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 在存储边界模拟校验后的改写；失败不得覆盖旧对象或允许提交未验证内容。 */
class ArtResourceObjectStoreTest {
    private static final String SOURCE = "resources/staging/op/";
    private static final String TARGET = "resources/frozen/op-unique/";
    private static final byte[] ORIGINAL = "art-resource".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static ArtResourceManifestVerifier.FileSpec file() {
        return new ArtResourceManifestVerifier.FileSpec("pack.bundle", ORIGINAL.length, ArtResourceManifestVerifier.sha256(ORIGINAL));
    }
    private static class Store extends ArtResourceObjectStore {
        final Map<String,byte[]> bytes = new HashMap<>();
        boolean mutateSecondRead; int sourceReads; int writes;
        Store(){ bytes.put(SOURCE+"pack.bundle", ORIGINAL.clone()); }
        @Override protected InputStream open(String key) throws IOException {
            if (key.startsWith(SOURCE) && ++sourceReads == 2 && mutateSecondRead) {
                byte[] changed=ORIGINAL.clone(); changed[0]^=1; bytes.put(key,changed);
            }
            byte[] value=bytes.get(key);
            if(value==null)throw new FileNotFoundException();
            return new ByteArrayInputStream(value);
        }
        @Override protected long size(String key) throws IOException {
            if(!bytes.containsKey(key))throw new FileNotFoundException();
            return bytes.get(key).length;
        }
        @Override protected void write(String key, InputStream input, long size) throws IOException {
            writes++; bytes.put(key,input.readAllBytes());
        }
        @Override protected boolean exists(String key){ return bytes.containsKey(key); }
    }
    @Test void verifiedBytesFreezeToIndependentPrefix() {
        Store store=new Store();store.freeze(SOURCE,TARGET,List.of(file()));
        assertArrayEquals(ORIGINAL,store.bytes.get(TARGET+"pack.bundle"));
        assertEquals(1,store.writes);
    }
    @Test void stagingWriteAfterFreezeCannotChangeFinalObject() {
        Store store=new Store();store.freeze(SOURCE,TARGET,List.of(file()));
        store.bytes.put(SOURCE+"pack.bundle",new byte[1]);
        assertArrayEquals(ORIGINAL,store.bytes.get(TARGET+"pack.bundle"));
    }
    @Test void stagingMutationBetweenValidationAndCopyRejected() {
        Store store=new Store();store.mutateSecondRead=true;
        assertThrows(ServiceException.class,()->store.freeze(SOURCE,TARGET,List.of(file())));
        assertEquals(1,store.writes);
    }
    @Test void existingFinalObjectCannotBeOverwritten() {
        Store store=new Store();store.bytes.put(TARGET+"pack.bundle",new byte[]{1});
        assertThrows(ServiceException.class,()->store.freeze(SOURCE,TARGET,List.of(file())));
        assertArrayEquals(new byte[]{1},store.bytes.get(TARGET+"pack.bundle"));assertEquals(0,store.writes);
    }
    @Test void wrongSizeOrHashRejectedBeforeWriting() {
        for(var bad:List.of(new ArtResourceManifestVerifier.FileSpec("pack.bundle",1,file().sha256()),
                new ArtResourceManifestVerifier.FileSpec("pack.bundle",ORIGINAL.length,"a".repeat(64)))){
            Store store=new Store();assertThrows(ServiceException.class,()->store.freeze(SOURCE,TARGET,List.of(bad)));assertEquals(0,store.writes);
        }
    }
    @Test void samePrefixOrRepeatedPathRejectedBeforeIO() {
        Store store=new Store();assertThrows(ServiceException.class,()->store.freeze(SOURCE,SOURCE,List.of(file())));
        assertThrows(ServiceException.class,()->store.freeze(SOURCE,TARGET,List.of(file(),file())));assertEquals(0,store.writes);assertEquals(0,store.sourceReads);
    }
    @Test void missingObjectCannotFreeze() {
        Store store=new Store();store.bytes.clear();assertThrows(ServiceException.class,()->store.freeze(SOURCE,TARGET,List.of(file())));assertEquals(0,store.writes);
    }
    @Test void manifestReadIsBounded() {
        Store store=new Store();store.bytes.put(SOURCE+"manifest.json",new byte[ArtResourceManifestVerifier.MAX_MANIFEST_BYTES+1]);
        assertThrows(ServiceException.class,()->store.readManifest(SOURCE,"manifest.json"));
    }
}
