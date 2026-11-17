package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dSceneController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证发布路由只负责参数投影，真实租户鉴权由服务和安全链路继续负责。 */
class Dim3dSceneControllerTest {
    @Test void managementCapabilitiesCurrentRevisionAndTicketsUseExplicitSafeContracts() throws Exception {
        var publication = mock(ScenePublicationService.class);
        var tickets = mock(ScenePublicationTicketService.class);
        var controller = new Dim3dSceneController();
        ReflectionTestUtils.setField(controller, "publicationService", publication);
        ReflectionTestUtils.setField(controller, "publicationTicketService", tickets);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(publication.capabilities()).thenReturn(Map.of("protocol", "fixed-ticket-v1"));
        when(publication.publishCurrent("2122269441737682946", 5, "request-stable"))
                .thenReturn(Map.of("publicationId", "PUB-10", "revisionNo", 5, "alreadyPublished", true));
        when(tickets.issue("PUB-10", 300)).thenReturn(Map.of("token", "test-only-token", "scope", "READ"));
        mvc.perform(get("/dim3d/scene/publication/capabilities")).andExpect(jsonPath("$.data.protocol").value("fixed-ticket-v1"));
        mvc.perform(post("/dim3d/scene/publication/publish-current").param("sceneId", "2122269441737682946").param("revisionNo", "5").param("requestId", "request-stable"))
                .andExpect(jsonPath("$.data.alreadyPublished").value(true));
        mvc.perform(post("/dim3d/scene/publication/PUB-10/ticket")).andExpect(header().string("Cache-Control", "no-store"));
        String guard = "@ss.hasAnyRoles('super_admin','tenant_admin')";
        assertEquals(guard, Dim3dSceneController.class.getDeclaredMethod("publishCurrent", String.class, int.class, String.class).getAnnotation(PreAuthorize.class).value());
        assertEquals(guard, Dim3dSceneController.class.getDeclaredMethod("publicationCapabilities").getAnnotation(PreAuthorize.class).value());
        var issue = Dim3dSceneController.class.getDeclaredMethod("issuePublicationTicket", String.class, int.class, jakarta.servlet.http.HttpServletResponse.class);
        assertEquals(guard, issue.getAnnotation(PreAuthorize.class).value());
        assertEquals(false, issue.getAnnotation(cn.iocoder.wx.framework.apilog.core.annotation.ApiAccessLog.class).responseEnable());
    }
    @Test void publicationRoutesDelegateToService() throws Exception {
        var scene = mock(Dim3dSceneService.class);
        var publication = mock(ScenePublicationService.class);
        var controller = new Dim3dSceneController();
        ReflectionTestUtils.setField(controller, "service", scene);
        ReflectionTestUtils.setField(controller, "publicationService", publication);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(publication.publish("101", 2, "request-1")).thenReturn(Map.of("publicationId", "PUB-1", "sceneId", "101", "revisionNo", 2, "status", "PUBLISHED"));
        when(publication.current("101")).thenReturn(Map.of("publicationId", "PUB-1", "sceneId", "101", "revisionNo", 2, "status", "PUBLISHED"));
        when(publication.readSnapshot("PUB-1")).thenReturn(Map.of("publicationId", "PUB-1", "dependencySnapshot", "{\"resources\":[]}", "indexVersion", 1));
        mvc.perform(post("/dim3d/scene/publication/publish").param("sceneId", "101").param("revisionNo", "2").param("requestId", "request-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publicationId").value("PUB-1"));
        mvc.perform(get("/dim3d/scene/publication/current/101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.revisionNo").value(2));
        mvc.perform(get("/dim3d/scene/publication/PUB-1/snapshot"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.indexVersion").value(1));
        verify(publication).publish("101", 2, "request-1");
        verify(publication).current("101");
        verify(publication).readSnapshot("PUB-1");
    }

    @Test void revokeAndRollbackRoutesKeepExplicitArguments() throws Exception {
        var scene = mock(Dim3dSceneService.class);
        var publication = mock(ScenePublicationService.class);
        var controller = new Dim3dSceneController();
        ReflectionTestUtils.setField(controller, "service", scene);
        ReflectionTestUtils.setField(controller, "publicationService", publication);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(publication.rollback("PUB-1", "rollback-1")).thenReturn(Map.of("publicationId", "PUB-2", "sceneId", "101", "revisionNo", 1, "status", "PUBLISHED"));
        mvc.perform(post("/dim3d/scene/publication/PUB-1/revoke").param("reason", "manual"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(post("/dim3d/scene/publication/PUB-1/rollback").param("requestId", "rollback-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publicationId").value("PUB-2"));
        verify(publication).revoke("PUB-1", "manual");
        verify(publication).rollback("PUB-1", "rollback-1");
    }

    @Test void mutatingPublicationRoutesKeepAdministratorGuard() throws Exception {
        var guard = "@ss.hasAnyRoles('super_admin','tenant_admin')";
        assertEquals(guard, Dim3dSceneController.class.getDeclaredMethod("publish", String.class, int.class, String.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals(guard, Dim3dSceneController.class.getDeclaredMethod("revokePublication", String.class, String.class)
                .getAnnotation(PreAuthorize.class).value());
        assertEquals(guard, Dim3dSceneController.class.getDeclaredMethod("rollbackPublication", String.class, String.class)
                .getAnnotation(PreAuthorize.class).value());
    }
}
