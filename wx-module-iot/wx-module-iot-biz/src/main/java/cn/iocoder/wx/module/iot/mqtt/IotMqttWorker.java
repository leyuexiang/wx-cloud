package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/** 单工作线程协调租约、数据库配置、网络会话及有上限退避，不持有本地消息队列。 */
@Slf4j
public class IotMqttWorker implements SmartLifecycle {
    private final IotMqttProperties properties;
    private final IotConnectorMapper connectors;
    private final TenantFrameworkService tenants;
    private final IotMqttLease lease;
    private final IotMqttConnection.Factory factory;
    private final ObjectMapper mapper;
    private ScheduledExecutorService scheduler;
    private volatile boolean running;
    private IotMqttConnection connection;
    private IotMqttSettings settings;
    private int failures;
    private long nextAttempt;
    private long connectedSince;

    public IotMqttWorker(IotMqttProperties properties, IotConnectorMapper connectors, TenantFrameworkService tenants,
                         IotMqttLease lease, IotMqttConnection.Factory factory, ObjectMapper mapper) {
        this.properties = properties;
        this.connectors = connectors;
        this.tenants = tenants;
        this.lease = lease;
        this.factory = factory;
        this.mapper = mapper;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "iot-mqtt-worker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::tick, 0, 5, TimeUnit.SECONDS);
    }

    /** 定时器与关闭互斥；所有数据库访问显式使用部署租户，不进行跨租户扫描。 */
    synchronized void tick() {
        if (!running) {
            return;
        }
        try {
            if (!lease.isValid()) {
                closeConnection();
            }
            if (!lease.acquireOrRenew()) {
                closeConnection();
                return;
            }
            TenantUtils.execute(properties.getTenantId(), () -> {
                tenants.validTenant(properties.getTenantId());
                IotConnectorDO connector = connectors.selectByConnectorId(properties.getConnectorId());
                if (connector == null || !Boolean.TRUE.equals(connector.getEnabled())
                        || !"MQTT".equals(connector.getConnectorType())) {
                    closeConnection();
                    failures = 0;
                    nextAttempt = 0;
                    return;
                }
                if (settings != null && !settings.matches(connector)) {
                    closeConnection();
                    nextAttempt = 0;
                }
                if (connection != null && connection.isConnected()) {
                    // 短连接后立刻遇到毒消息时不清零退避，只有稳定运行后才重置故障计数。
                    if (System.nanoTime() - connectedSince >= TimeUnit.SECONDS.toNanos(60)) {
                        failures = 0;
                    }
                    updateStatus(connector, "CONNECTED");
                    return;
                }
                if (connection != null) {
                    closeConnection();
                    updateStatus(connector, "DISCONNECTED");
                    throw new IllegalStateException("MQTT 接收链路已断开");
                }
                closeConnection();
                if (nextAttempt != 0 && System.nanoTime() - nextAttempt < 0) {
                    return;
                }
                settings = IotMqttSettings.parse(connector, properties.isAllowInsecure(), mapper);
                updateStatus(connector, "DISCONNECTED");
                try {
                    connection = factory.create(settings, lease);
                    connection.connect();
                    if (!lease.isValid() || !connection.isConnected()) {
                        throw new IllegalStateException("MQTT 连接完成时租约已失效");
                    }
                    connectedSince = System.nanoTime();
                    updateStatus(connector, "CONNECTED");
                } catch (Exception ex) {
                    closeConnection();
                    updateStatus(connector, "DISCONNECTED");
                    throw new IllegalStateException("MQTT 连接或订阅失败");
                }
            });
        } catch (RuntimeException ex) {
            closeConnection();
            failures = Math.min(failures + 1, 7);
            long delay = Math.min(300, (1L << failures) * 2 + ThreadLocalRandom.current().nextLong(3));
            nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(delay);
            // 底层异常可能含用户名、地址、JSON 或 SQL，不记录原始异常文本。
            log.warn("MQTT 接入暂不可用，tenantId={}, connectorId={}, retrySeconds={}",
                    properties.getTenantId(), properties.getConnectorId(), delay);
        }
    }

    private void updateStatus(IotConnectorDO connector, String status) {
        if (!status.equals(connector.getStatus())) {
            connectors.update(null, new LambdaUpdateWrapper<IotConnectorDO>()
                    .eq(IotConnectorDO::getId, connector.getId()).eq(IotConnectorDO::getEnabled, true)
                    // 有效的动态所有者受保护；崩溃遗留的过期所有者不能永久阻止冷切回单连接模式。
                    .and(owner -> owner.isNull(IotConnectorDO::getRuntimeOwner)
                            .or().isNull(IotConnectorDO::getRuntimeLeaseUntil)
                            .or().apply("runtime_lease_until <= NOW(3)"))
                    .eq(IotConnectorDO::getVersion, connector.getVersion())
                    .eq(IotConnectorDO::getConnectorType, "MQTT")
                    .eq(IotConnectorDO::getEndpoint, connector.getEndpoint())
                    // 凭据引用变更后，旧连接同样不得覆盖新配置的运行状态。
                    .eq(IotConnectorDO::getCredentialRef, connector.getCredentialRef())
                    .eq(IotConnectorDO::getConfigJson, connector.getConfigJson())
                    .set(IotConnectorDO::getStatus, status));
            connector.setStatus(status);
        }
    }

    private void closeConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        settings = null;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        closeConnection();
        try {
            lease.release();
        } catch (RuntimeException ex) {
            log.warn("MQTT 进程退出时未能释放租约，将等待租约到期，tenantId={}, connectorId={}",
                    properties.getTenantId(), properties.getConnectorId());
        }
    }

    @Override
    public boolean isRunning() { return running; }
}
