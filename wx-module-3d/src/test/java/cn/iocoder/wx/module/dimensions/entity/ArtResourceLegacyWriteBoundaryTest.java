package cn.iocoder.wx.module.dimensions.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 旧目录请求不能写入新关联，避免旧接口成为绕过资源事务的入口。 */
class ArtResourceLegacyWriteBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void modelRequestCannotRebindResource() throws Exception {
        Dim3dModel body = mapper.readValue(
                "{\"modelName\":\"普通汽车\",\"category\":\"TEST\",\"modelFileId\":101,\"resourceId\":999}",
                Dim3dModel.class);
        assertNull(body.getResourceId());
        // 旧文件编号仍可正常解析，不能为禁止改绑而破坏整个旧请求。
        assertEquals(101L, body.getModelFileId());
    }

    @Test
    void iconRequestCannotRebindResource() throws Exception {
        Dim3dIcon body = mapper.readValue(
                "{\"iconName\":\"图标\",\"category\":\"TEST\",\"iconFileId\":102,\"resourceId\":999}",
                Dim3dIcon.class);
        assertNull(body.getResourceId());
        assertEquals(102L, body.getIconFileId());
    }

    @Test
    void templateRequestCannotRebindResource() throws Exception {
        Dim3dTemplate body = mapper.readValue(
                "{\"templateName\":\"模板\",\"category\":\"TEST\",\"templateFileId\":103,\"resourceId\":999}",
                Dim3dTemplate.class);
        assertNull(body.getResourceId());
        assertEquals(103L, body.getTemplateFileId());
    }
}
