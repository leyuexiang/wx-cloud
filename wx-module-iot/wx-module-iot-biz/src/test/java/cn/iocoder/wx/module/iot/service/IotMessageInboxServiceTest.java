package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.framework.common.exception.ServiceException;
import cn.iocoder.wx.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.wx.module.iot.api.IotValueType;
import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotMessageInboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 JSON 序列化器验证事件快照可重放，数据库与 Kafka 通过替身隔离。 */
class IotMessageInboxServiceTest {
    private final IotMessageInboxMapper mapper = mock(IotMessageInboxMapper.class);
    private final IotEventPublisher publisher = mock(IotEventPublisher.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final IotMessageInboxServiceImpl service = new IotMessageInboxServiceImpl(mapper, json, publisher);

    /** 每个用例设置独立租户上下文，模拟管理入口的租户隔离前置条件。 */
    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
    }

    /** 清除线程变量，防止后续测试继承本用例的租户。 */
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    /** 已保存的标准事件必须能反序列化，且重放租户取数据库记录而非不可信快照。 */
    @Test
    void shouldReplaySnapshotAndRestoreTenant() throws Exception {
        prepareReplay("FAILED");
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        service.replay("event-1");
        verify(publisher).publish(argThat(event -> event.getTenantId().equals(7L)
                && event.getEventId().equals("event-1")
                && event.getEventTime().equals(Instant.parse("2026-10-17T12:00:00Z"))));
        verify(mapper).updateStatus(eq("event-1"), anyString(), eq("PUBLISHED"), isNull());
        assertEquals(7L, TenantContextHolder.getTenantId());
    }

    /** 异步发送失败应写入失败状态，不能提前报告发布成功。 */
    @Test
    void shouldMarkAsFailedWhenAsyncPublishFails() throws Exception {
        prepareReplay("DEAD_LETTER");
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        service.replay("event-1");
        verify(mapper).markFailed(eq("event-1"), anyString(), eq("broker unavailable"), eq(5));
        verify(mapper, never()).updateStatus(anyString(), anyString(), anyString(), any());
    }

    /** 同步发送失败与失败状态落库同时报错时，仍应向调用方保留原始发送错误。 */
    @Test
    void shouldPreservePublishFailureWhenRecordingAlsoFails() throws Exception {
        prepareReplay("FAILED");
        RuntimeException publishError = new IllegalStateException("broker unavailable");
        RuntimeException databaseError = new IllegalStateException("database unavailable");
        when(publisher.publish(any())).thenThrow(publishError);
        when(mapper.markFailed(anyString(), anyString(), anyString(), anyInt())).thenThrow(databaseError);
        RuntimeException actual = assertThrows(RuntimeException.class, () -> service.replay("event-1"));
        assertSame(publishError, actual);
        assertEquals(1, actual.getSuppressed().length);
        // 租户工具会包装数据库异常；仍必须保留其真实根因。
        assertSame(databaseError, actual.getSuppressed()[0].getCause());
    }

    /** 未找到事件时应明确失败，避免对不存在的数据执行发布。 */
    @Test
    void shouldRejectMissingEvent() {
        assertCode(INBOX_EVENT_NOT_EXISTS.getCode(), () -> service.replay("event-1"));
        verifyNoInteractions(publisher);
    }

    /** 已发布事件不可人工重放，防止绕过去重边界。 */
    @Test
    void shouldRejectPublishedEvent() throws Exception {
        prepareReplay("PUBLISHED");
        assertCode(INBOX_REPLAY_NOT_ALLOWED.getCode(), () -> service.replay("event-1"));
        verifyNoInteractions(publisher);
    }

    /** 历史快照连接器与数据库记录不一致时，不允许继续抢占或投递。 */
    @Test
    void shouldRejectMismatchedConnector() throws Exception {
        IotMessageInboxDO inbox = prepareReplay("FAILED");
        inbox.setConnectorId("another-connector");
        assertCode(INBOX_PAYLOAD_INVALID.getCode(), () -> service.replay("event-1"));
        verifyNoInteractions(publisher);
    }

    /** 并发重放只有成功取得抢占的调用可以发布。 */
    @Test
    void shouldNotPublishWhenReplayClaimLost() throws Exception {
        prepareReplay("FAILED");
        when(mapper.forceReclaim(anyString(), anyString(), any())).thenReturn(0);
        assertCode(INBOX_REPLAY_NOT_ALLOWED.getCode(), () -> service.replay("event-1"));
        verifyNoInteractions(publisher);
    }

    /** 普通插入故障不可被当成重复消息；只有唯一键冲突才允许重抢占。 */
    @Test
    void shouldPropagateInsertFailure() {
        RuntimeException databaseError = new IllegalStateException("database unavailable");
        when(mapper.insertNew(any())).thenThrow(databaseError);
        RuntimeException actual = assertThrows(RuntimeException.class, () -> service.tryClaim(event()));
        // 租户工具允许包装异常，但不能吞掉原始数据库故障或走重复消息分支。
        assertSame(databaseError, actual.getCause());
        verify(mapper, never()).reclaim(anyString(), anyString(), any(), anyInt());
    }

    /** 唯一键冲突且当前记录不可抢占时，返回空令牌并保持原租户上下文。 */
    @Test
    void shouldReturnNullForDuplicateWithoutClaim() {
        when(mapper.insertNew(any())).thenThrow(new DuplicateKeyException("duplicate"));
        assertNull(service.tryClaim(event()));
        verify(mapper).reclaim(eq("event-1"), anyString(), any(), eq(5));
        assertEquals(7L, TenantContextHolder.getTenantId());
    }

    /** 构造真实序列化的历史快照，特意使快照租户不同于记录租户。 */
    private IotMessageInboxDO prepareReplay(String status) throws Exception {
        IotMessageInboxDO inbox = new IotMessageInboxDO();
        inbox.setEventId("event-1");
        inbox.setConnectorId("connector-1");
        inbox.setTenantId(7L);
        inbox.setStatus(status);
        inbox.setPayloadJson(json.writeValueAsString(event()));
        when(mapper.selectByEventId("event-1")).thenReturn(inbox);
        when(mapper.forceReclaim(anyString(), anyString(), any())).thenReturn(1);
        return inbox;
    }

    /** 使用固定时间、编号和数值类型，保证快照测试可复现。 */
    private TwinTelemetryEvent event() {
        return TwinTelemetryEvent.builder().eventId("event-1").connectorId("connector-1")
                .tenantId(99L).eventTime(Instant.parse("2026-10-17T12:00:00Z"))
                .valueType(IotValueType.NUMBER).value(12).build();
    }

    /** 验证业务错误码，避免仅断言异常类型而遗漏错误分支。 */
    private void assertCode(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ServiceException.class, action).getCode());
    }
}
