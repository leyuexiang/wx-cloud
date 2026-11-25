package cn.iocoder.wx.module.iot.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** MQTT 3.1.1 持久会话；数据库持久接收成功之后才显式发送 QoS 1 确认。 */
public class PahoIotMqttConnection implements IotMqttConnection {
    private static final Logger log = LoggerFactory.getLogger(PahoIotMqttConnection.class);
    private final MqttAsyncClient client;
    private final IotMqttSettings settings;
    private final IotMqttSettings.Credentials credentials;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycle = new Object();
    private final Executor receiverExecutor;
    private final IotMqttLease lease;
    private final long generation;
    private final BiConsumer<String, byte[]> receiver;
    private final Semaphore inFlight;

    public PahoIotMqttConnection(MqttAsyncClient client, IotMqttSettings settings,
            IotMqttSettings.Credentials credentials, IotMqttLease lease, BiConsumer<String, byte[]> receiver) {
        // 既有嵌入调用保留同步语义；应用装配显式传入共享接收池。
        this(client, settings, credentials, lease, receiver, null, 1);
    }

    public PahoIotMqttConnection(MqttAsyncClient client, IotMqttSettings settings,
            IotMqttSettings.Credentials credentials, IotMqttLease lease, BiConsumer<String, byte[]> receiver,
            Executor receiverExecutor, int maxInFlight) {
        if (maxInFlight < 1 || maxInFlight > 256) throw new IllegalArgumentException("MQTT 在途上限无效");
        this.client = client;
        this.settings = settings;
        this.credentials = credentials;
        this.lease = lease;
        this.receiver = receiver;
        this.receiverExecutor = receiverExecutor;
        this.generation = lease.generation();
        this.inFlight = new Semaphore(maxInFlight, true);
        client.setManualAcks(true);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // 不在网络回调中重连；运行时使用统一退避和租约检查重新建立连接。
                deactivate();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                try {
                    requireCurrent();
                    byte[] payload = message.getPayload();
                    if (message.getQos() != 1 || payload == null || payload.length == 0 || payload.length > 65536) {
                        throw new IllegalStateException("MQTT 会话已失效或发布方未采用 QoS 1");
                    }
                    if (receiverExecutor == null) {
                        receiveAndAcknowledge(topic, payload, message.getId());
                        return;
                    }
                    // 单连接满额时背压，等待期间定期核对关闭/租约，不能永久阻塞网络回调。
                    while (!inFlight.tryAcquire(100, TimeUnit.MILLISECONDS)) requireCurrent();
                    FutureTask<Void> task;
                    try {
                        requireCurrent();
                        // 任务拥有独立字节副本；名额已取得且长度已限制，队列不会保留无界载荷。
                        byte[] owned = Arrays.copyOf(payload, payload.length);
                        int packetId = message.getId();
                        task = new FutureTask<>(() -> {
                            try { receiveAndAcknowledge(topic, owned, packetId); }
                            catch (Exception error) { fail(error); }
                            return null;
                        }) {
                            @Override protected void done() {
                                if (isCancelled()) deactivate();
                                inFlight.release();
                            }
                        };
                    } catch (Exception error) {
                        inFlight.release();
                        throw error;
                    }
                    try { receiverExecutor.execute(task); }
                    catch (RuntimeException error) { task.cancel(false); throw error; }
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    fail(ex);
                    throw ex;
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 此客户端仅订阅，不发布下行消息。
            }
        });
    }

    private void requireCurrent() {
        if (closed.get() || !active.get() || !lease.isValid() || generation != lease.generation()
                || Thread.currentThread().isInterrupted()
                || receiverExecutor instanceof IotMqttReceiveDispatcher dispatcher && dispatcher.isShutdown()) {
            throw new IllegalStateException("MQTT 接收会话、租约代次或接收池已失效");
        }
    }

    private void receiveAndAcknowledge(String topic, byte[] payload, int packetId) throws MqttException {
        // 同步入口刚完成检查；异步任务可能已排队，执行前必须重新检查。
        if (receiverExecutor != null) requireCurrent();
        receiver.accept(topic, payload);
        synchronized (lifecycle) {
            // 与关闭互斥，落库后再次确认代次；排队成功或迟到完成都不能触发旧会话确认。
            requireCurrent();
            client.messageArrivedComplete(packetId, 1);
        }
    }

    private void deactivate() {
        synchronized (lifecycle) { active.set(false); }
    }

    private void fail(Exception error) {
        deactivate();
        // 异步失败由现有运行时发现并关闭会话，未确认消息继续留在代理持久会话内。
        log.warn("MQTT 持久接收失败，failureType={}", error.getClass().getSimpleName());
    }

    @Override
    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(false);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        options.setHttpsHostnameVerificationEnabled(true);
        options.setUserName(credentials.username());
        options.setPassword(credentials.password());
        try {
            client.connect(options).waitForCompletion(10000);
            String[] topics = settings.topics().toArray(String[]::new);
            int[] qualities = new int[topics.length];
            Arrays.fill(qualities, 1);
            var subscribed = client.subscribe(topics, qualities);
            subscribed.waitForCompletion(10000);
            // Broker 可拒绝订阅或降为 QoS 0，此时不能宣称可靠接入已经建立。
            if (subscribed.getGrantedQos() == null || subscribed.getGrantedQos().length != topics.length
                    || Arrays.stream(subscribed.getGrantedQos()).anyMatch(qos -> qos != 1)) {
                throw new MqttException(MqttException.REASON_CODE_SUBSCRIBE_FAILED);
            }
        } finally {
            Arrays.fill(credentials.password(), '\0');
        }
    }

    @Override
    public boolean isConnected() {
        return active.get() && client.isConnected();
    }

    @Override
    public void close() {
        // 失效回收与应用关闭可能重叠，Paho 释放只能执行一次。
        if (!closed.compareAndSet(false, true)) return;
        deactivate();
        Arrays.fill(credentials.password(), '\0');
        try {
            // 异常回调或尚未完成连接时也应释放客户端，避免重试累积网络线程。
            client.disconnectForcibly(1000, 1000, false);
        } catch (MqttException ignored) {
            // 已断开状态仍继续 close；不输出可能携带服务地址或报文的底层错误。
        } finally {
            try {
                client.close(true);
            } catch (MqttException ignored) {
                // 强制关闭过程中客户端已释放时无需再次重连。
            }
        }
    }
}
