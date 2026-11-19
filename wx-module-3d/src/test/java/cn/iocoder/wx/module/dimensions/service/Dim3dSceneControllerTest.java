package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.dimensions.controller.admin.Dim3dSceneController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证发布路由只负责参数投影，真实租户鉴权由服务和安全链路继续负责。 */
class Dim3dSceneControllerTest {
    /** 场景管理根级返回列表和记录总数，避免再拆解数据包装。 */
    @Test void scenePageReturnsRootRows() throws Exception {
        var scene = mock(Dim3dSceneService.class);
        var controller = new Dim3dSceneController();
        ReflectionTestUtils.setField(controller, "service", scene);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(scene.page(null, null, null, "INSTANCE", null, null, 1, 10, null, null))
                .thenReturn(new PageResult<>(List.of(Map.of("sceneId", "9007199254740993")), 1L));
        mvc.perform(get("/dim3d/scene/page").param("sceneKind", "INSTANCE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].sceneId").value("9007199254740993"))
                .andExpect(jsonPath("$.data").doesNotHaveJsonPath());
    }

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

    /** 页面使用固定发布能力和确认入口，授权及编辑维护使用文档约定的空回执。 */
    @Test void managementRoutesMatchContract() throws Exception {
        var scene = mock(Dim3dSceneService.class);
        var publication = mock(ScenePublicationService.class);
        var grants = mock(SceneTenantGrantService.class);
        var controller = new Dim3dSceneController();
        ReflectionTestUtils.setField(controller, "service", scene);
        ReflectionTestUtils.setField(controller, "publicationService", publication);
        ReflectionTestUtils.setField(controller, "grants", grants);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(scene.copy("101")).thenReturn(Map.of("sceneId", "9007199254740993", "revisionNo", 1));
        when(publication.capabilities()).thenReturn(Map.of("accessModes", List.of(Map.of("value", "TICKET"))));
        when(publication.publishCurrent("101", 2, "publish-1")).thenReturn(Map.of("publicationId", "PUB-1"));
        when(grants.list("101")).thenReturn(List.of(Map.of("grantTenantId", "9007199254740994", "status", 1)));
        mvc.perform(post("/dim3d/scene/copy/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneId").value("9007199254740993"));
        mvc.perform(get("/dim3d/scene/publication/capabilities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessModes[0].value").value("TICKET"));
        mvc.perform(post("/dim3d/scene/publication/publish-current").param("sceneId", "101")
                        .param("revisionNo", "2").param("requestId", "publish-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.publicationId").value("PUB-1"));
        String grantForm = "{\"sceneId\":\"101\",\"grantTenantId\":\"9007199254740994\"}";
        for (String action : List.of("grant", "revoke")) {
            mvc.perform(post("/dim3d/scene/" + action).contentType("application/json").content(grantForm))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        }
        mvc.perform(get("/dim3d/scene/authorizedTenants/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].grantTenantId").value("9007199254740994"));
        mvc.perform(delete("/dim3d/scene/remove/101")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(delete("/dim3d/scene/unlock/101").param("clientKey", "page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        mvc.perform(put("/dim3d/scene/edit/101").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(nullValue()));
        verify(grants).grant("101", "9007199254740994");
        verify(grants).revoke("101", "9007199254740994");
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
