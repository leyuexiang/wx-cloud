package cn.iocoder.wx.module.iot.service;

import cn.iocoder.wx.module.iot.api.TwinTelemetryEvent;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxPageReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotMessageInboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** 使用数据库唯一约束实现跨实例事件去重。 */
@Service
@Slf4j
public class IotMessageInboxServiceImpl implements IotMessageInboxService {

    private static final String STATUS_CLAIMED = "CLAIMED";
    public static final String PUBLISHED_STATUS = "PUBLISHED";
    public static final String ARCHIVE_STATUS = "ARCHIVED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_DEAD_LETTER = "DEAD_LETTER";
    private static final int MAX_RETRY_COUNT = 5;
    private static final int MAX_ARCHIVE_BATCH_SIZE = 1000;

    private final IotMessageInboxMapper inboxMapper;
    private final ObjectReader eventReader;
    private final ObjectWriter eventWriter;
    private final IotEventPublisher eventPublisher;

    /** 快照使用独立读写器，避免网页毫秒配置影响恢复或丢失新事件的纳秒精度。 */
    public IotMessageInboxServiceImpl(IotMessageInboxMapper inboxMapper, ObjectMapper objectMapper,
                                      IotEventPublisher eventPublisher) {
        this.inboxMapper = inboxMapper;
        this.eventPublisher = eventPublisher;
        eventWriter = objectMapper.writerFor(TwinTelemetryEvent.class)
                .without(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 兼容现场旧整数毫秒快照；旧小数秒及新的 ISO 字符串仍由标准时间解析器处理。
        eventReader = objectMapper.readerFor(TwinTelemetryEvent.class)
                .without(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @Override
    public String tryClaim(TwinTelemetryEvent event) {
        if (event.getTenantId() != null) {
            return TenantUtils.execute(event.getTenantId(), () -> doTryClaim(event));
        }
        return doTryClaim(event);
    }

    /** 在已绑定租户上下文中执行唯一键抢占，确保外部适配器显式租户号时不会串租户。 */
    private String doTryClaim(TwinTelemetryEvent event) {
        IotMessageInboxDO inbox = new IotMessageInboxDO();
        inbox.setEventId(event.getEventId());
        inbox.setConnectorId(event.getConnectorId());
        inbox.setExternalDeviceId(event.getExternalDeviceId());
        inbox.setPointCode(event.getPointCode());
        try {
            inbox.setPayloadJson(eventWriter.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_PAYLOAD_INVALID);
        }
        inbox.setStatus(STATUS_CLAIMED);
        inbox.setClaimToken(UUID.randomUUID().toString());
        inbox.setReceivedAt(LocalDateTime.now());
        try {
            inboxMapper.insertNew(inbox);
            return inbox.getClaimToken();
        } catch (DuplicateKeyException ex) {
            // 只有唯一键冲突才进入重抢占；字段约束和数据库故障必须向接入端报错。
            return inboxMapper.reclaim(event.getEventId(), inbox.getClaimToken(), inbox.getReceivedAt(),
                    MAX_RETRY_COUNT) > 0 ? inbox.getClaimToken() : null;
        }
    }

    @Override
    public PageResult<IotMessageInboxDO> getPage(IotMessageInboxPageReqVO pageReqVO) {
        return inboxMapper.selectPage(pageReqVO);
    }

    @Override
    public void replay(String eventId) {
        IotMessageInboxDO inbox = inboxMapper.selectByEventId(eventId);
        if (inbox == null) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_EVENT_NOT_EXISTS);
        }
        if (!STATUS_FAILED.equals(inbox.getStatus()) && !STATUS_DEAD_LETTER.equals(inbox.getStatus())) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_REPLAY_NOT_ALLOWED);
        }
        if (inbox.getPayloadJson() == null || inbox.getPayloadJson().isBlank()) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_PAYLOAD_INVALID);
        }
        TwinTelemetryEvent event;
        try {
            event = eventReader.readValue(inbox.getPayloadJson());
        } catch (JsonProcessingException ex) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_PAYLOAD_INVALID);
        }
        if (event == null || !Objects.equals(eventId, event.getEventId())
                || !Objects.equals(inbox.getConnectorId(), event.getConnectorId())) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_PAYLOAD_INVALID);
        }
        // 重放租户必须来自当前隔离范围内的 Inbox 记录，不能信任历史快照中的租户字段。
        Long tenantId = inbox.getTenantId();
        event.setTenantId(tenantId);
        String claimToken = UUID.randomUUID().toString();
        if (!forceReclaim(tenantId, eventId, claimToken)) {
            throw cn.iocoder.wx.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.wx.module.iot.enums.ErrorCodeConstants.INBOX_REPLAY_NOT_ALLOWED);
        }
        try {
            eventPublisher.publish(event).whenComplete((result, throwable) -> {
                try {
                    if (throwable == null) {
                        markPublished(tenantId, eventId, claimToken);
                    } else {
                        markFailed(tenantId, eventId, claimToken, throwable);
                    }
                } catch (RuntimeException recordingError) {
                    // 人工重放同样可能在响应后落库失败，必须记录原租户、事件和 Kafka 确认结果。
                    // 不能在这里自动重放已确认消息；失败记录保留现有超时重新抢占路径。
                    log.error("物联网异步投递状态更新失败，tenantId={}, eventId={}, publishSucceeded={}",
                            tenantId, eventId, throwable == null, recordingError);
                }
            });
        } catch (RuntimeException ex) {
            // 同步发送失败时尝试更新状态；落库也失败时仍保留原始发送错误。
            try {
                markFailed(tenantId, eventId, claimToken, ex);
            } catch (RuntimeException recordingError) {
                // 状态未更新时仍可由既有超时抢占机制恢复，两个故障一起向调用方暴露。
                if (recordingError != ex) {
                    ex.addSuppressed(recordingError);
                }
            }
            throw ex;
        }
    }

    @Override
    public void markPublished(Long tenantId, String eventId, String claimToken) {
        updateStatus(tenantId, eventId, claimToken, PUBLISHED_STATUS, null);
    }

    @Override
    public void markFailed(Long tenantId, String eventId, String claimToken, Throwable throwable) {
        String message = throwable == null ? "未知发布错误" : throwable.getMessage();
        if (message != null && message.length() > 500) {
            message = message.substring(0, 500);
        }
        updateFailed(tenantId, eventId, claimToken, message);
    }

    private void updateStatus(Long tenantId, String eventId, String claimToken, String status, String errorMessage) {
        if (tenantId == null) {
            inboxMapper.updateStatus(eventId, claimToken, status, errorMessage);
            return;
        }
        TenantUtils.execute(tenantId, () -> inboxMapper.updateStatus(eventId, claimToken, status, errorMessage));
    }

    private void updateFailed(Long tenantId, String eventId, String claimToken, String errorMessage) {
        if (tenantId == null) {
            inboxMapper.markFailed(eventId, claimToken, errorMessage, MAX_RETRY_COUNT);
            return;
        }
        TenantUtils.execute(tenantId, () -> inboxMapper.markFailed(eventId, claimToken, errorMessage, MAX_RETRY_COUNT));
    }

    private boolean forceReclaim(Long tenantId, String eventId, String claimToken) {
        if (tenantId == null) {
            return inboxMapper.forceReclaim(eventId, claimToken, LocalDateTime.now()) > 0;
        }
        return TenantUtils.execute(tenantId,
                () -> inboxMapper.forceReclaim(eventId, claimToken, LocalDateTime.now()) > 0);
    }

    @Override
    public int archivePublishedBefore(LocalDateTime before, int batchSize) {
        Objects.requireNonNull(before, "归档截止时间不能为空");
        if (batchSize < 1 || batchSize > MAX_ARCHIVE_BATCH_SIZE) {
            throw new IllegalArgumentException("归档批量大小必须在 1 到 " + MAX_ARCHIVE_BATCH_SIZE + " 之间");
        }
        // 归档任务沿用当前租户上下文；不提供跨租户全表更新入口，避免误归档其他租户数据。
        return inboxMapper.archivePublishedBefore(before, batchSize);
    }
}
