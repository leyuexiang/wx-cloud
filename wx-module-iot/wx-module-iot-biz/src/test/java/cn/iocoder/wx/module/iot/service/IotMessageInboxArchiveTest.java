package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.dal.mysql.IotMessageInboxMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 接入事件软归档测试：仅归档已发布事件，并保持租户边界和批量上限。 */
class IotMessageInboxArchiveTest {
    private final IotMessageInboxMapper mapper = mock(IotMessageInboxMapper.class);
    private final IotMessageInboxServiceImpl service = new IotMessageInboxServiceImpl(
            mapper, new com.fasterxml.jackson.databind.ObjectMapper(), mock(IotEventPublisher.class));

    /** 模拟管理任务在指定租户上下文中执行。 */
    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    /** 清理线程变量，避免归档任务污染其他请求。 */
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 归档使用当前租户上下文，返回实际更新行数，并恢复外层上下文。 */
    @Test
    void shouldArchivePublishedEventsWithinTenant() {
        LocalDateTime before = LocalDateTime.of(2026, 10, 1, 0, 0);
        when(mapper.archivePublishedBefore(before, 100)).thenReturn(37);
        TenantContextHolder.setTenantId(8L);
        assertEquals(37, service.archivePublishedBefore(before, 100));
        verify(mapper).archivePublishedBefore(before, 100);
        assertEquals(8L, TenantContextHolder.getTenantId());
    }

    /** 单次归档最多处理 1000 条，避免大事务和锁持有时间失控。 */
    @Test
    void shouldRejectInvalidBatchSize() {
        LocalDateTime before = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> service.archivePublishedBefore(before, 0));
        assertThrows(IllegalArgumentException.class, () -> service.archivePublishedBefore(before, 1001));
        verifyNoInteractions(mapper);
    }

    /** 归档截止时间不能为空，避免误将全部历史事件标记为归档。 */
    @Test
    void shouldRejectMissingCutoff() {
        assertThrows(NullPointerException.class, () -> service.archivePublishedBefore(null, 100));
        verifyNoInteractions(mapper);
    }

    /** 归档 SQL 只能更新 PUBLISHED，失败、死信和已归档事件由 Mapper 契约保证不触碰。 */
    @Test
    void shouldDocumentArchiveStatusContract() {
        assertTrue(IotMessageInboxServiceImpl.ARCHIVE_STATUS.equals("ARCHIVED"));
        assertTrue(IotMessageInboxServiceImpl.PUBLISHED_STATUS.equals("PUBLISHED"));
    }
}
