package cn.iocoder.wx.module.iot.dal.mysql;

import cn.iocoder.wx.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.wx.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorRuntimeDO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorPageReqVO;
import cn.iocoder.wx.framework.common.pojo.PageResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 物联网连接器配置数据访问层。 */
@Mapper
public interface IotConnectorMapper extends BaseMapperX<IotConnectorDO> {

    /** 状态和期限在同一次查询中观测；即使误启用忽略租户，也保留显式租户条件。 */
    @Select("SELECT id, connector_id, connector_type, enabled, version, status, runtime_lease_until, "
            + "NOW(3) AS observed_at, (runtime_owner IS NOT NULL) AS owner_present, "
            + "CASE WHEN runtime_owner IS NOT NULL AND runtime_lease_until > NOW(3) THEN 1 ELSE 0 END AS lease_active "
            + "FROM wx_iot_connector WHERE tenant_id = #{tenantId} AND id = #{id} AND deleted = b'0'")
    IotConnectorRuntimeDO selectRuntime(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /** 分页与筛选由数据库完成，不对旧全量列表在内存中截断。 */
    default PageResult<IotConnectorDO> selectManagementPage(Long tenantId, IotConnectorPageReqVO request) {
        return selectPage(request, new LambdaQueryWrapperX<IotConnectorDO>()
                .eq(IotConnectorDO::getTenantId, tenantId)
                .likeIfPresent(IotConnectorDO::getConnectorId, request.getConnectorId())
                .eqIfPresent(IotConnectorDO::getConnectorType, request.getConnectorType())
                .eqIfPresent(IotConnectorDO::getEnabled, request.getEnabled())
                .orderByDesc(IotConnectorDO::getId));
    }

    /** 有界游标查询显式限定部署租户，绝不绕过租户插件扫描其他租户。 */
    @Select("SELECT * FROM wx_iot_connector WHERE tenant_id = #{tenantId} AND id > #{afterId} "
            + "AND enabled = 1 AND connector_type = 'MQTT' AND deleted = b'0' ORDER BY id LIMIT #{limit}")
    List<IotConnectorDO> selectEnabledMqttBatch(@Param("tenantId") Long tenantId,
            @Param("afterId") long afterId, @Param("limit") int limit);

    /** 数据库期限约束所有权接管；不能仅凭 Redis 请求发起前仍有效就覆盖新所有者。 */
    @Update("UPDATE wx_iot_connector SET runtime_owner = #{owner}, "
            + "runtime_lease_until = DATE_ADD(NOW(3), INTERVAL 60 SECOND), status = 'DISCONNECTED' "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} "
            + "AND enabled = 1 AND connector_type = 'MQTT' AND deleted = b'0' "
            + "AND (runtime_owner IS NULL OR runtime_lease_until IS NULL OR runtime_lease_until <= NOW(3))")
    int claimMqttRuntime(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Integer version, @Param("owner") String owner);

    @Update("UPDATE wx_iot_connector SET runtime_lease_until = DATE_ADD(NOW(3), INTERVAL 60 SECOND) "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND version = #{version} "
            + "AND runtime_owner = #{owner} AND runtime_lease_until > NOW(3) "
            + "AND enabled = 1 AND connector_type = 'MQTT' AND deleted = b'0'")
    int renewMqttRuntime(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Integer version, @Param("owner") String owner);

    @Update("UPDATE wx_iot_connector SET status = #{status} WHERE tenant_id = #{tenantId} "
            + "AND id = #{id} AND version = #{version} AND runtime_owner = #{owner} "
            + "AND runtime_lease_until > NOW(3) AND enabled = 1 AND connector_type = 'MQTT' AND deleted = b'0'")
    int updateMqttRuntimeStatus(@Param("tenantId") Long tenantId, @Param("id") Long id,
            @Param("version") Integer version, @Param("owner") String owner, @Param("status") String status);

    /** 释放只匹配原令牌；配置版本变化后仍允许旧实例清理自己的所有权。 */
    @Update("UPDATE wx_iot_connector SET runtime_owner = NULL, runtime_lease_until = NULL, "
            + "status = CASE WHEN enabled = 1 THEN 'DISCONNECTED' ELSE 'DISABLED' END "
            + "WHERE tenant_id = #{tenantId} AND id = #{id} AND runtime_owner = #{owner} AND deleted = b'0'")
    int releaseMqttRuntime(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("owner") String owner);

    default IotConnectorDO selectByConnectorId(String connectorId) {
        return selectOne(IotConnectorDO::getConnectorId, connectorId);
    }

    default List<IotConnectorDO> selectList(String connectorId, Boolean enabled) {
        return selectList(new LambdaQueryWrapperX<IotConnectorDO>()
                .likeIfPresent(IotConnectorDO::getConnectorId, connectorId)
                .eqIfPresent(IotConnectorDO::getEnabled, enabled)
                .orderByDesc(IotConnectorDO::getId));
    }
}
