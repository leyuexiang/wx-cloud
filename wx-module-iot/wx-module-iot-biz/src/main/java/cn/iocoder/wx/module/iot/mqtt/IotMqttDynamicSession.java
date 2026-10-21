package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单连接控制状态机；网络建连和关闭均异步执行，控制周期继续续租。 */
@Slf4j
final class IotMqttDynamicSession {
    final AtomicBoolean scheduled = new AtomicBoolean();
    final CompletableFuture<Void> closed = new CompletableFuture<>();
    final IotMqttSettings settings;
    private final IotConnectorMapper connectors;
    private final TenantFrameworkService tenants;
    private final IotMqttLease lease;
    private final IotMqttConnection.Factory factory;
    private final Executor connections;
    private final Executor cleanups;
    private volatile boolean retired;
    private volatile boolean retirementStarted;
    private CompletableFuture<IotMqttConnection> attempt;
    private CompletableFuture<Void> closing;
    private boolean claimed;
    private String owner;
    private String status;
    private int failures;
    private long nextAttempt;
    private long connectedSince;

    IotMqttDynamicSession(IotMqttSettings settings, IotConnectorMapper connectors, TenantFrameworkService tenants,
            IotMqttLease lease, IotMqttConnection.Factory factory, Executor connections, Executor cleanups) {
        this.settings = settings;
        this.connectors = connectors;
        this.tenants = tenants;
        this.lease = lease;
        this.factory = factory;
        this.connections = connections;
        this.cleanups = cleanups;
    }

    boolean matches(IotConnectorDO desired) { return settings.matches(desired); }

    boolean needsRetirement() { return retired && !retirementStarted; }

    /** 先无锁撤销确认资格，之后由控制线程等待在途建连结束并释放资源。 */
    void retire() {
        retired = true;
        lease.retire();
    }

    synchronized void tick() {
        try {
            if (retired) {
                return;
            }
            if (closing != null) {
                if (!closing.isDone()) {
                    return;
                }
                closing.join();
                closing = null;
            }
            // 已经认领的所有权一旦超时，整个代次作废，不复用租约对象复活旧客户端。
            if (claimed && !lease.isValid()) {
                retire();
                return;
            }
            if (!lease.acquireOrRenew()) {
                if (claimed) {
                    retire();
                }
                return;
            }
            TenantUtils.execute(settings.tenantId(), this::controlInTenant);
        } catch (RuntimeException ex) {
            retire();
            // 不输出底层报错，以免 URI、SQL、密码或报文原文进入运行日志。
            log.warn("MQTT 动态会话控制失败，tenantId={}, connectorId={}", settings.tenantId(), settings.connectorId());
        } finally {
            if (retired) {
                finishRetirement();
            }
        }
    }

    private void controlInTenant() {
        tenants.validTenant(settings.tenantId());
        IotConnectorDO current = connectors.selectById(settings.id());
        if (current == null || !Boolean.TRUE.equals(current.getEnabled())
                || !"MQTT".equals(current.getConnectorType()) || !matches(current)) {
            retire();
            return;
        }
        if (!claimed) {
            owner = lease.ownerToken();
            if (owner == null || connectors.claimMqttRuntime(settings.tenantId(), settings.id(), settings.version(), owner) != 1) {
                return;
            }
            claimed = true;
            status = "DISCONNECTED";
        } else if (connectors.renewMqttRuntime(settings.tenantId(), settings.id(), settings.version(), owner) != 1) {
            retire();
            return;
        }
        if (!lease.isValid() || retired) {
            retire();
            return;
        }
        if (attempt != null) {
            if (!attempt.isDone()) {
                return;
            }
            if (attempt.isCompletedExceptionally()) {
                // 建连任务在返回失败之前已经关闭客户端，这里只推进退避。
                attempt = null;
                failed();
                return;
            }
            IotMqttConnection connection = attempt.join();
            if (connection.isConnected()) {
                if (connectedSince == 0) {
                    connectedSince = System.nanoTime();
                } else if (System.nanoTime() - connectedSince >= TimeUnit.SECONDS.toNanos(60)) {
                    failures = 0;
                }
                updateStatus("CONNECTED");
                return;
            }
            failed();
            closeAttempt();
            return;
        }
        if (nextAttempt != 0 && System.nanoTime() - nextAttempt < 0) {
            return;
        }
        try {
            // 执行器无等待队列；达到建连并发上限时下个周期再尝试，不占用续租线程。
            attempt = CompletableFuture.supplyAsync(this::openConnection, connections);
        } catch (RejectedExecutionException ex) {
            nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        }
    }

    private IotMqttConnection openConnection() {
        IotMqttConnection connection = null;
        boolean succeeded = false;
        try {
            if (retired || !lease.isValid()) {
                throw new IllegalStateException("MQTT 会话已失效");
            }
            connection = factory.create(settings, lease);
            connection.connect();
            if (retired || !lease.isValid() || !connection.isConnected()) {
                throw new IllegalStateException("MQTT 连接完成时会话已失效");
            }
            succeeded = true;
            return connection;
        } catch (Exception ex) {
            throw new IllegalStateException("MQTT 建连或订阅失败");
        } finally {
            if (!succeeded && connection != null) {
                connection.close();
            }
        }
    }

    private void failed() {
        failures = Math.min(failures + 1, 8);
        long delay = Math.min(300, (1L << failures) * 2 + ThreadLocalRandom.current().nextLong(3));
        nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos(delay);
        connectedSince = 0;
        lease.invalidateCallbacks();
        updateStatus("DISCONNECTED");
        log.warn("MQTT 动态会话等待重连，tenantId={}, connectorId={}, retrySeconds={}",
                settings.tenantId(), settings.connectorId(), delay);
    }

    private void updateStatus(String next) {
        if (!next.equals(status)) {
            if (retired || !lease.isValid() || connectors.updateMqttRuntimeStatus(
                    settings.tenantId(), settings.id(), settings.version(), owner, next) != 1) {
                retire();
                return;
            }
            status = next;
        }
    }

    private void closeAttempt() {
        lease.invalidateCallbacks();
        CompletableFuture<IotMqttConnection> previous = attempt;
        attempt = null;
        connectedSince = 0;
        closing = previous == null ? CompletableFuture.completedFuture(null)
                : previous.handle((connection, error) -> connection).thenAcceptAsync(connection -> {
                    if (connection != null) {
                        connection.close();
                    }
                }, cleanups);
    }

    private void finishRetirement() {
        if (retirementStarted) {
            return;
        }
        retirementStarted = true;
        if (closing == null) {
            closeAttempt();
        }
        closing.whenComplete((ignored, closeError) -> {
            try {
                if (claimed) {
                    TenantUtils.execute(settings.tenantId(), () -> connectors.releaseMqttRuntime(
                            settings.tenantId(), settings.id(), owner));
                }
            } catch (RuntimeException ex) {
                log.warn("MQTT 数据库所有权释放失败，将等待到期，tenantId={}, connectorId={}",
                        settings.tenantId(), settings.connectorId());
            } finally {
                try {
                    lease.release();
                } catch (RuntimeException ex) {
                    log.warn("MQTT Redis 租约释放失败，将等待到期，tenantId={}, connectorId={}",
                            settings.tenantId(), settings.connectorId());
                }
                if (closeError == null) {
                    closed.complete(null);
                } else {
                    // 无法证明客户端已关闭时保留容量占位，避免连续重建积累未知资源。
                    log.error("MQTT 客户端关闭失败，需要检查实例，tenantId={}, connectorId={}",
                            settings.tenantId(), settings.connectorId());
                    closed.completeExceptionally(new IllegalStateException("MQTT 客户端关闭失败"));
                }
            }
        });
    }
}
