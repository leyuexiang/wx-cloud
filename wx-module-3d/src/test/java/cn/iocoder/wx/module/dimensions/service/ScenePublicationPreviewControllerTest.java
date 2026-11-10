package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.web.core.handler.GlobalExceptionHandler;
import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.infra.api.logger.ApiErrorLogApi;
import cn.iocoder.wx.module.dimensions.controller.admin.ScenePublicationPreviewController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 请求不能用路径、正文或查询参数覆盖票据身份，匿名范围仅为专用预览路由。 */
class ScenePublicationPreviewControllerTest {
    @Test void resourceRoutesUseTicketHeaderAndRejectIdentityOverrides() throws Exception {
        var files = mock(ScenePublicationFileService.class);
        var controller = new ScenePublicationPreviewController();
        ReflectionTestUtils.setField(controller, "files", files);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler("data09-test", mock(ApiErrorLogApi.class))).build();
        when(files.list("header-ticket")).thenReturn(Map.of("publicationId", "PUB-1", "resources", java.util.List.of()));
        when(files.file("header-ticket", "model", "manifest.json")).thenReturn(Map.of("fileUrl", "https://example.invalid/unit-file"));
        mvc.perform(post("/dim3d/scene/publication/preview/resources").header("X-Scene-Ticket", "header-ticket"))
                .andExpect(jsonPath("$.data.publicationId").value("PUB-1")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/dim3d/scene/publication/preview/file").header("X-Scene-Ticket", "header-ticket")
                        .contentType("application/json").content("{\"resourceKey\":\"model\",\"path\":\"manifest.json\"}"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/dim3d/scene/publication/preview/file").header("X-Scene-Ticket", "header-ticket")
                        .contentType("application/json").content("{\"resourceKey\":\"model\",\"path\":\"manifest.json\",\"tenantId\":3}"))
                .andExpect(jsonPath("$.code").value(400)).andExpect(header().string("Cache-Control", "no-store"));
        verify(files).list("header-ticket"); verify(files).file("header-ticket", "model", "manifest.json");
        verifyNoMoreInteractions(files);
    }

    @Test void missingTicketReturnsExplicitFailureWithoutCaching() throws Exception {
        var tickets = mock(ScenePublicationTicketService.class);
        var controller = new ScenePublicationPreviewController();
        ReflectionTestUtils.setField(controller, "tickets", tickets);
        when(tickets.consumeSnapshot(null)).thenThrow(new ServiceException(401, "访问票据无效"));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler("data09-test", mock(ApiErrorLogApi.class))).build();
        mvc.perform(post("/dim3d/scene/publication/preview"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test void headerOnlyConsumptionAndNoStoreResponse() throws Exception {
        var tickets = mock(ScenePublicationTicketService.class);
        var controller = new ScenePublicationPreviewController();
        ReflectionTestUtils.setField(controller, "tickets", tickets);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(tickets.consumeSnapshot("header-ticket")).thenReturn(Map.of("publicationId", "PUB-1", "sceneJson", "{}"));
        mvc.perform(post("/dim3d/scene/publication/preview").header("X-Scene-Ticket", "header-ticket")
                        .param("tenantId", "99").param("publicationId", "PUB-other"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.publicationId").value("PUB-1"));
        // 即使调用者将票据写入参数，本入口也不读取参数中的票据。
        mvc.perform(post("/dim3d/scene/publication/preview").param("ticket", "query-ticket"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/dim3d/scene/publication/preview")).andExpect(status().isMethodNotAllowed());
        verify(tickets).consumeSnapshot("header-ticket");
        verify(tickets).consumeSnapshot(null);
        verifyNoMoreInteractions(tickets);
    }
}
