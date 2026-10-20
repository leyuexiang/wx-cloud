package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** 解析实际 Mapper SQL 并核对所有权条件，不将语法契约检查当作真实数据库执行。 */
class IotMqttRuntimeSqlTest {
    private String sql(String method) {
        Method selected = Arrays.stream(IotConnectorMapper.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
        return selected.isAnnotationPresent(Update.class) ? String.join(" ", selected.getAnnotation(Update.class).value())
                : String.join(" ", selected.getAnnotation(Select.class).value());
    }

    @Test
    void shouldParseDiscoveryAndOwnershipSqlWithExplicitTenantAndLogicalDeletion() {
        for (String method : new String[]{"selectEnabledMqttBatch", "claimMqttRuntime", "renewMqttRuntime",
                "updateMqttRuntimeStatus", "releaseMqttRuntime"}) {
            String sql = sql(method);
            assertTrue(sql.contains("tenant_id = #{tenantId}"));
            assertTrue(sql.contains("deleted = b'0'"));
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql.replaceAll("#\\{[^}]+}", "1")));
        }
    }

    @Test
    void shouldFenceRenewalAndStatusByOwnerVersionAndDatabaseDeadline() {
        for (String method : new String[]{"renewMqttRuntime", "updateMqttRuntimeStatus"}) {
            String sql = sql(method);
            assertTrue(sql.contains("runtime_owner = #{owner}"));
            assertTrue(sql.contains("version = #{version}"));
            assertTrue(sql.contains("runtime_lease_until > NOW(3)"));
        }
        assertTrue(sql("claimMqttRuntime").contains("runtime_lease_until <= NOW(3)"));
        assertTrue(sql("releaseMqttRuntime").contains("runtime_owner = #{owner}"));
    }
}
