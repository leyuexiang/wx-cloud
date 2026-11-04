package cn.iocoder.wx.module.iot.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/** MQTT 3.1.1 持久会话；数据库持久接收成功之后才显式发送 QoS 1 确认。 */
public class PahoIotMqttConnection implements IotMqttConnection {
    private final MqttAsyncClient client;
    private final IotMqttSettings settings;
    private final IotMqttSettings.Credentials credentials;
    private final AtomicBoolean active = new AtomicBoolean(true);

    public PahoIotMqttConnection(MqttAsyncClient client, IotMqttSettings settings,
            IotMqttSettings.Credentials credentials, IotMqttLease lease, BiConsumer<String, byte[]> receiver) {
        this.client = client;
        this.settings = settings;
        this.credentials = credentials;
        long generation = lease.generation();
        client.setManualAcks(true);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                // 不在网络回调中重连；运行时使用统一退避和租约检查重新建立连接。
                active.set(false);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                try {
                    if (!active.get() || !lease.isValid() || generation != lease.generation() || message.getQos() != 1) {
                        throw new IllegalStateException("MQTT 会话已失效或发布方未采用 QoS 1");
                    }
                    receiver.accept(topic, message.getPayload());
                    // 写库期间可能发生租约超时或停用；旧会话不得继续确认新所有者应接收的消息。
                    if (!active.get() || !lease.isValid() || generation != lease.generation()) {
                        throw new IllegalStateException("MQTT 接收确认前会话或租约失效");
                    }
                    client.messageArrivedComplete(message.getId(), message.getQos());
                } catch (Exception ex) {
                    active.set(false);
                    throw ex;
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 此客户端仅订阅，不发布下行消息。
            }
        });
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
        active.set(false);
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
