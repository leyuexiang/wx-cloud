package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.controller.admin.ModelImportController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC协议检查与默认关闭验证；不替代真实网关认证及对象存储验收。 */
class ModelImportControllerTest {
    @Test void routeIsNotRegisteredByDefault() {
        new ApplicationContextRunner().withUserConfiguration(ModelImportController.class).run(context->{
            assertNull(context.getStartupFailure());assertEquals(0,context.getBeansOfType(ModelImportController.class).size());
        });
    }
    @Test void submitReturnsAcceptedAndCommittedReceiptReturnsOk() throws Exception {
        var service=mock(ModelImportService.class);var controller=new ModelImportController();
        ReflectionTestUtils.setField(controller,"service",service);ReflectionTestUtils.setField(controller,"parser",new ArtResourceManifestVerifier());
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        when(service.submit("op")).thenReturn(ModelImportStatusView.from("op","101","QUEUED","UPLOADING","glb",null,null,null,false));
        mvc.perform(post("/dim3d/model-imports/op/submit").contentType("application/json").content("{}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.importStatus").value("QUEUED"));
        when(service.submit("op")).thenReturn(ModelImportStatusView.from("op","101","FAILED","COMMITTED","glb",null,"1",null,false));
        mvc.perform(post("/dim3d/model-imports/op/submit").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.committedRevision").value("1"));
    }
    @Test void commandBodiesCannotInjectParameters() throws Exception {
        var service=mock(ModelImportService.class);var controller=new ModelImportController();
        ReflectionTestUtils.setField(controller,"service",service);ReflectionTestUtils.setField(controller,"parser",new ArtResourceManifestVerifier());
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        assertThrows(Exception.class,()->mvc.perform(post("/dim3d/model-imports/op/submit").contentType("application/json").content("{\"tenantId\":\"3\"}")));
        assertThrows(Exception.class,()->mvc.perform(post("/dim3d/model-imports/op/source-target").contentType("application/json").content("{} {}")));
        verifyNoInteractions(service);
    }
}
