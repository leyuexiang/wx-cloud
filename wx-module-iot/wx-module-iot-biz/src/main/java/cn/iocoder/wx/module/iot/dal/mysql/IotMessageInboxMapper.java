package cn.iocoder.wx.module.iot.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import cn.iocoder.wx.module.iot.controller.admin.messageinbox.vo.IotMessageInboxPageReqVO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotMessageInboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 遥测事件 Inbox 数据访问层。 */
@Mapper
public interface IotMessageInboxMapper extends BaseMapperX<IotMessageInboxDO> {

    /** 依赖数据库唯一键抢占事件；数据库错误不能被静默当成重复消息。 */
    @Insert("""
            INSERT INTO iot_message_inbox
                (event_id, connector_id, external_device_id, point_code, payload_json, status, claim_token, retry_count, received_at, create_time, update_time, deleted)
            VALUES
                (#{eventId}, #{connectorId}, #{externalDeviceId}, #{pointCode}, #{payloadJson}, #{status}, #{claimToken}, 0, #{receivedAt}, NOW(), NOW(), b'0')
            """)
    int insertNew(IotMessageInboxDO inbox);

    default IotMessageInboxDO selectByEventId(String eventId) {
        return selectOne(IotMessageInboxDO::getEventId, eventId);
    }

    default PageResult<IotMessageInboxDO> selectPage(IotMessageInboxPageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<IotMessageInboxDO>()
                .eqIfPresent(IotMessageInboxDO::getConnectorId, pageReqVO.getConnectorId())
                .eqIfPresent(IotMessageInboxDO::getExternalDeviceId, pageReqVO.getExternalDeviceId())
                .eqIfPresent(IotMessageInboxDO::getStatus, pageReqVO.getStatus())
                .betweenIfPresent(IotMessageInboxDO::getReceivedAt, pageReqVO.getReceivedAt())
                .orderByDesc(IotMessageInboxDO::getReceivedAt));
    }

    /** 失败事件或超时占用事件允许重新抢占，避免服务崩溃后永久卡在 CLAIMED。 */
    @Update("UPDATE iot_message_inbox SET status = 'CLAIMED', claim_token = #{claimToken}, received_at = #{receivedAt}, "
            + "error_message = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = b'0' "
            + "AND retry_count < #{maxRetryCount} "
            + "AND (status = 'FAILED' OR (status = 'CLAIMED' AND received_at < DATE_SUB(NOW(), INTERVAL 5 MINUTE)))")
    int reclaim(@Param("eventId") String eventId,
                @Param("claimToken") String claimToken,
                @Param("receivedAt") java.time.LocalDateTime receivedAt,
                @Param("maxRetryCount") int maxRetryCount);

    /** 人工重放可以重置失败或死信事件的重试次数，但仍保持原始事件编号。 */
    @Update("UPDATE iot_message_inbox SET status = 'CLAIMED', claim_token = #{claimToken}, retry_count = 0, received_at = #{receivedAt}, "
            + "error_message = NULL, update_time = NOW() WHERE event_id = #{eventId} AND deleted = b'0' "
            + "AND status IN ('FAILED', 'DEAD_LETTER')")
    int forceReclaim(@Param("eventId") String eventId,
                     @Param("claimToken") String claimToken,
                     @Param("receivedAt") java.time.LocalDateTime receivedAt);

    /** 只允许当前抢占者写入发布结果，避免旧回调覆盖新一轮投递。 */
    @Update("UPDATE iot_message_inbox SET status = CASE WHEN retry_count + 1 >= #{maxRetryCount} "
            + "THEN 'DEAD_LETTER' ELSE 'FAILED' END, retry_count = retry_count + 1, "
            + "error_message = #{errorMessage}, update_time = NOW() "
            + "WHERE event_id = #{eventId} AND claim_token = #{claimToken} AND status = 'CLAIMED' AND deleted = b'0'")
    int markFailed(@Param("eventId") String eventId,
                   @Param("claimToken") String claimToken,
                   @Param("errorMessage") String errorMessage,
                   @Param("maxRetryCount") int maxRetryCount);

    @Update("UPDATE iot_message_inbox SET status = #{status}, error_message = #{errorMessage}, update_time = NOW() "
            + "WHERE event_id = #{eventId} AND claim_token = #{claimToken} AND status = 'CLAIMED' AND deleted = b'0'")
    int updateStatus(@Param("eventId") String eventId,
                     @Param("claimToken") String claimToken,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);
}
