package cn.iocoder.wx.module.iot.mqtt;

import cn.iocoder.wx.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.wx.framework.tenant.core.util.TenantUtils;
import cn.iocoder.wx.module.iot.dal.dataobject.IotConnectorDO;
import cn.iocoder.wx.module.iot.dal.mysql.IotConnectorMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** 在部署租户范围内管理有上限的会话；发现、续租控制、建连和关闭分别调度。 */
@Slf4j
public class IotMqttDynamicManager implements SmartLifecycle {
    private final IotMqttProperties properties;
    private final IotConnectorMapper connectors;
    private final TenantFrameworkService tenants;
    private final ObjectMapper mapper;
    private final Function<IotConnectorDO, IotMqttLease> leases;
    private final IotMqttConnection.Factory factory;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService controls;
    private final ExecutorService connections;
    private final ExecutorService cleanups;
    private final Map<Long, IotMqttDynamicSession> sessions = new ConcurrentHashMap<>();
    private volatile boolean running;
    private CompletableFuture<Void> termination = CompletableFuture.completedFuture(null);

    public IotMqttDynamicManager(IotMqttProperties properties, IotConnectorMapper connectors,
            TenantFrameworkService tenants, ObjectMapper mapper, Function<IotConnectorDO, IotMqttLease> leases,
            IotMqttConnection.Factory factory) {
        this(properties, connectors, tenants, mapper, leases, factory,
                Executors.newScheduledThreadPool(2, threads("iot-mqtt-scan")),
                new ThreadPoolExecutor(properties.getControlConcurrency(), properties.getControlConcurrency(),
                        0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(properties.getMaxConnections()), threads("iot-mqtt-control")),
                new ThreadPoolExecutor(properties.getConnectConcurrency(), properties.getConnectConcurrency(),
                        0, TimeUnit.SECONDS, new SynchronousQueue<>(), threads("iot-mqtt-connect")),
                new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(properties.getMaxConnections()), threads("iot-mqtt-close")));
    }

    IotMqttDynamicManager(IotMqttProperties properties, IotConnectorMapper connectors,
            TenantFrameworkService tenants, ObjectMapper mapper, Function<IotConnectorDO, IotMqttLease> leases,
            IotMqttConnection.Factory factory, ScheduledExecutorService scheduler, ExecutorService controls,
            ExecutorService connections, ExecutorService cleanups) {
        this.properties = properties;
        this.connectors = connectors;
        this.tenants = tenants;
        this.mapper = mapper;
        this.leases = leases;
        this.factory = factory;
        this.scheduler = scheduler;
        this.controls = controls;
        this.connections = connections;
        this.cleanups = cleanups;
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (scheduler.isShutdown()) {
            throw new IllegalStateException("MQTT 管理器资源已释放，请重新创建应用实例");
        }
        running = true;
        termination = new CompletableFuture<>();
        scheduler.scheduleWithFixedDelay(this::discover, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::pollSessions, 0, 5, TimeUnit.SECONDS);
    }

    /** 一次最多读取容量加一条，额外记录只用于报告超额，不创建无界待接入队列。 */
    void discover() {
        if (!running) {
            return;
        }
        try {
            List<IotConnectorDO> discovered = TenantUtils.execute(properties.getTenantId(), () -> {
                tenants.validTenant(properties.getTenantId());
                List<IotConnectorDO> result = new ArrayList<>();
                long cursor = 0;
                while (result.size() <= properties.getMaxConnections()) {
                    int limit = Math.min(properties.getDiscoveryBatchSize(), properties.getMaxConnections() + 1 - result.size());
                    List<IotConnectorDO> batch = connectors.selectEnabledMqttBatch(properties.getTenantId(), cursor, limit);
                    result.addAll(batch);
                    if (batch.size() < limit) {
                        break;
                    }
                    cursor = batch.get(batch.size() - 1).getId();
                }
                return result;
            });
            Map<Long, IotConnectorDO> desired = new LinkedHashMap<>();
            discovered.stream().limit(properties.getMaxConnections()).forEach(row -> desired.put(row.getId(), row));
            if (discovered.size() > properties.getMaxConnections()) {
                log.warn("MQTT 连接器超过实例配置容量，tenantId={}, maxConnections={}",
                        properties.getTenantId(), properties.getMaxConnections());
            }
            synchronized (sessions) {
                if (!running) {
                    return;
                }
                sessions.entrySet().removeIf(entry -> entry.getValue().closed.isDone()
                        && !entry.getValue().closed.isCompletedExceptionally());
                sessions.forEach((id, session) -> {
                    IotConnectorDO row = desired.get(id);
                    if (row == null || !session.matches(row)) {
                        session.retire();
                        schedule(session);
                    }
                });
                for (IotConnectorDO row : desired.values()) {
                    if (sessions.size() >= properties.getMaxConnections()) {
                        break;
                    }
                    if (!sessions.containsKey(row.getId())) {
                        try {
                            IotMqttSettings settings = IotMqttSettings.parse(row, properties.isAllowInsecure(), mapper);
                            sessions.put(row.getId(), new IotMqttDynamicSession(settings, connectors, tenants,
                                    leases.apply(row), factory, connections, cleanups));
                        } catch (RuntimeException ex) {
                            log.warn("MQTT 连接器参数无效，tenantId={}, connectorId={}", properties.getTenantId(), row.getConnectorId());
                        }
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn("MQTT 连接器发现失败，tenantId={}", properties.getTenantId());
        }
    }

    void pollSessions() {
        if (running) {
            sessions.values().forEach(this::schedule);
        }
    }

    private void schedule(IotMqttDynamicSession session) {
        if (!session.scheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            controls.execute(() -> {
                try {
                    session.tick();
                } finally {
                    session.scheduled.set(false);
                    // 覆盖控制任务结束和停用同时发生的竞态，不能漏掉最后一次清理。
                    if (session.needsRetirement()) {
                        schedule(session);
                    }
                }
            });
        } catch (RejectedExecutionException ex) {
            session.scheduled.set(false);
            log.warn("MQTT 控制执行器暂不可用，tenantId={}", properties.getTenantId());
        }
    }

    @Override
    public void stop() { stop(() -> { }); }

    @Override
    public synchronized void stop(Runnable callback) {
        if (!running) {
            // 重复停止也必须等待同一批资源清理完成，不能提前宣告应用可以销毁依赖。
            termination.whenComplete((ignored, error) -> callback.run());
            return;
        }
        termination.whenComplete((ignored, error) -> callback.run());
        List<IotMqttDynamicSession> retiring;
        synchronized (sessions) {
            running = false;
            retiring = new ArrayList<>(sessions.values());
            retiring.forEach(IotMqttDynamicSession::retire);
        }
        scheduler.shutdownNow();
        retiring.forEach(this::schedule);
        // 正常关闭执行器，保留在途建连及清理回调，不能取消队列后丢失资源所有者。
        connections.shutdown();
        CompletableFuture.allOf(retiring.stream().map(session -> session.closed).toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    controls.shutdown();
                    cleanups.shutdown();
                    sessions.clear();
                    termination.complete(null);
                });
    }

    @Override
    public boolean isRunning() { return running; }
}
