package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.exception.ModelImportErrorCode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 覆盖提交后崩溃、投影滞后和错误回显，避免把入队当成资源发布。 */
class ModelImportStatusViewTest {
    @Test void serializedResponsesRemainStableIncludingNulls() throws Exception {
        String op="6063365a-210b-486b-8c2d-928b10e66a39";
        var views=List.of(
            ModelImportStatusView.from(op,"101","FAILED","COMMITTED","glb",null,"2",null,true),
            ModelImportStatusView.from(op,"101","QUEUED","UPLOADING","glb",null,null,null,false),
            ModelImportStatusView.from(op,"101","FAILED","FAILED","gltf",op,null,ModelImportErrorCode.CONVERTER_BUSY,false));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        var tree=mapper.valueToTree(views);
        assertTrue(tree.get(1).has("committedRevision"));assertTrue(tree.get(1).get("committedRevision").isNull());
        var output=java.nio.file.Path.of("target","market01-status-examples.json");
        java.nio.file.Files.createDirectories(output.getParent());
        mapper.writeValue(output.toFile(),views);
    }
    @Test void committedReceiptOverridesStaleFailedProjection() {
        var view=ModelImportStatusView.from("op","101","FAILED","COMMITTED","glb",null,"2",ModelImportErrorCode.CONVERTER_TIMEOUT,false);
        assertEquals("COMMITTED",view.importStatus());assertEquals("2",view.committedRevision());
        assertFalse(view.retryable());assertNull(view.errorCode());
    }
    @Test void projectionCannotInventSuccessOrRevision() {
        assertThrows(ServiceException.class,()->ModelImportStatusView.from("op","101","COMMITTED","UPLOADING","glb",null,null,null,false));
        for(String revision:Arrays.asList(null,"0","01","9223372036854775808"))
            assertThrows(ServiceException.class,()->ModelImportStatusView.from("op","101","FAILED","COMMITTED","glb",null,revision,null,false));
    }
    @Test void terminalOperationOverridesStaleRunningTask() {
        for(String state:List.of("CANCELLED","CONFLICT","EXPIRED")) {
            var view=ModelImportStatusView.from("op","101","CONVERTING",state,"gltf",null,null,ModelImportErrorCode.CONVERTER_TIMEOUT,false);
            assertEquals(state,view.importStatus());assertFalse(view.retryable());assertNull(view.committedRevision());
        }
    }
    @Test void retryabilityRequiresFailureRecoverableErrorAndLiveOperation() {
        assertTrue(ModelImportStatusView.from("op","101","FAILED","UPLOADING","glb",null,null,ModelImportErrorCode.CONVERTER_BUSY,false).retryable());
        assertFalse(ModelImportStatusView.from("op","101","QUEUED","UPLOADING","glb",null,null,ModelImportErrorCode.CONVERTER_BUSY,false).retryable());
        assertFalse(ModelImportStatusView.from("op","101","FAILED","FAILED","glb",null,null,ModelImportErrorCode.INVALID_MODEL,false).retryable());
        assertFalse(ModelImportStatusView.from("op","101","FAILED","FAILED","glb",null,null,ModelImportErrorCode.CONVERTER_BUSY,true).retryable());
        assertFalse(ModelImportStatusView.from("op","101","FAILED","FAILED","glb",null,null,null,false).retryable());
    }
    @Test void errorNumbersUniqueAndMessagesDoNotComeFromToolOutput() {
        Set<Integer> codes=new HashSet<>();
        for(var error:ModelImportErrorCode.values()) {
            assertTrue(codes.add(error.getCode()));assertFalse(error.getMsg().isBlank());
        }
        var view=ModelImportStatusView.from("op","101","FAILED","FAILED","glb",null,null,ModelImportErrorCode.ARTIFACT_UNAVAILABLE,false);
        assertEquals("ARTIFACT_UNAVAILABLE",view.errorCode());assertFalse(view.retryable());
    }
}
