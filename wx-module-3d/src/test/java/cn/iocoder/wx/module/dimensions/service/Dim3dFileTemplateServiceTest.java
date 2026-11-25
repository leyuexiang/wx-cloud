package cn.iocoder.wx.module.dimensions.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.module.dimensions.entity.Dim3dTemplate;
import cn.iocoder.wx.module.dimensions.mapper.Dim3dTemplateMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实内存数据库验证普通模板目录、租户范围和状态事务。 */
class Dim3dFileTemplateServiceTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private ArtResourceAccess access;
    private Dim3dFileTemplateService service;

    @BeforeEach
    void setup() {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE wx_3d_template(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, template_name VARCHAR(50), template_desc VARCHAR(500),
                  template_type VARCHAR(16), category VARCHAR(32), resource_belong VARCHAR(16),
                  cover_image_id BIGINT, template_file_id BIGINT, status VARCHAR(16),
                  data_user_id BIGINT, data_dept_id BIGINT, create_dept BIGINT, tenant_id VARCHAR(20),
                  create_by BIGINT, create_time TIMESTAMP, update_by BIGINT, update_time TIMESTAMP,
                  del_flag CHAR(1), resource_id BIGINT)
                """);
        jdbc.execute("CREATE TABLE wx_3d_resource(id BIGINT PRIMARY KEY,resource_scope VARCHAR(32),content_state VARCHAR(16))");
        access = mock(ArtResourceAccess.class);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7001, 41));
        service = new Dim3dFileTemplateService();
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "access", access);
        ReflectionTestUtils.setField(service, "transactions", new DataSourceTransactionManager(source));
    }

    @AfterEach
    void closeDatabase() { jdbc.execute("SHUTDOWN"); }

    /** 通过原目录映射执行真实新增，验证自增身份、模板类型转换及可信审计。 */
    @Test
    void createUsesCanonicalTypeAndTrustedAudit() {
        var config = new MybatisConfiguration(new Environment("template-test", new JdbcTransactionFactory(), source));
        config.addMapper(Dim3dTemplateMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)) {
            ReflectionTestUtils.setField(service, "templateMapper", session.getMapper(Dim3dTemplateMapper.class));
            var input = template();
            input.setTenantId("62");
            input.setCreateBy(13L);
            input.setStatus("OFFLINE");
            var saved = service.get(service.create(input));
            assertEquals("41", saved.getTenantId());
            assertEquals(7001L, saved.getCreateBy());
            assertNotNull(saved.getCreateTime());
            assertEquals("INDUSTRY", saved.getTemplateType());
            assertEquals("ONLINE", saved.getStatus());
        }
    }

    @Test
    void pageFiltersTenantAndSortsBeforePagination() {
        insert(10L, "B", "41", "0");
        insert(20L, "A", "41", "0");
        insert(30L, "A", "41", "0");
        insert(40L, "A", "62", "0");
        insert(50L, "A", "41", "1");

        var page = service.page(null, "dim3d_template_industry", "PARK_INDUSTRY", "MINE", "OFFLINE", 1, 2, "templateName", "asc");
        assertEquals(3L, page.getTotal());
        assertEquals(List.of(30L, 20L), page.getList().stream().map(Dim3dTemplate::getId).toList());
        assertEquals(400, assertThrows(ServiceException.class,
                () -> service.page(null, null, null, null, null, 1, 10, "tenantId", "asc")).getCode());
    }

    /** 编辑保留未提交字段，状态重放保留审计，逻辑删除保留已保存的文件关联。 */
    @Test
    void editPreservesStatusAndOptionalFieldsAndStatusReplayKeepsAudit() {
        insert(10L, "旧名称", "41", "0");
        service.update(10L, template());
        var saved = service.get(10L);
        assertEquals("新名称", saved.getTemplateName());
        assertEquals("OFFLINE", saved.getStatus());
        assertEquals("旧描述", saved.getTemplateDesc());
        assertEquals(77L, saved.getCoverImageId());
        service.changeStatus(10L, "ONLINE");
        var online = service.get(10L);
        when(access.actor()).thenReturn(new ArtResourceAccess.Actor(7002, 41));
        service.changeStatus(10L, "ONLINE");
        assertEquals(online.getUpdateBy(), service.get(10L).getUpdateBy());
        assertEquals(online.getUpdateTime(), service.get(10L).getUpdateTime());
        service.delete(10L);
        assertEquals(404, assertThrows(ServiceException.class, () -> service.get(10L)).getCode());
        assertEquals(9007199254740994L, jdbc.queryForObject("SELECT template_file_id FROM wx_3d_template WHERE id=10", Long.class));
    }

    @Test
    void writesRejectOtherTenantAndGuardManagedCatalogRecords() {
        insert(10L, "其他租户", "62", "0");
        assertEquals(404, assertThrows(ServiceException.class, () -> service.update(10L, template())).getCode());
        assertEquals(404, assertThrows(ServiceException.class, () -> service.delete(10L)).getCode());
        assertEquals(400, assertThrows(ServiceException.class, () -> service.changeStatus(10L, "offline")).getCode());
        insert(20L, "受管模板", "41", "0");
        jdbc.update("INSERT INTO wx_3d_resource VALUES(77,'TENANT_SHARED','READY')");
        jdbc.update("UPDATE wx_3d_template SET resource_id=77 WHERE id=20");
        assertEquals(cn.iocoder.wx.module.dimensions.exception.ArtResourceErrorCode.NOT_VISIBLE.getCode(),
                assertThrows(ServiceException.class, () -> service.changeStatus(20L, "ONLINE")).getCode());
    }

    private static Dim3dTemplate template() {
        var template = new Dim3dTemplate();
        template.setTemplateName("新名称");
        template.setTemplateType("dim3d_template_industry");
        template.setCategory("PARK_INDUSTRY");
        template.setResourceBelong("MINE");
        template.setTemplateFileId(9007199254740994L);
        return template;
    }

    private void insert(long id, String name, String tenant, String deleted) {
        jdbc.update("""
                INSERT INTO wx_3d_template(id,template_name,template_desc,template_type,category,resource_belong,
                  tenant_id,cover_image_id,template_file_id,status,del_flag)
                VALUES(?,?,'旧描述','INDUSTRY','PARK_INDUSTRY','MINE',?,77,8,'OFFLINE',?)
                """, id, name, tenant, deleted);
    }
}
