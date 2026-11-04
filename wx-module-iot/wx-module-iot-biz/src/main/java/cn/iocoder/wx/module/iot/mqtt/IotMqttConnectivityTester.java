package cn.iocoder.wx.module.iot.mqtt;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO;
import cn.iocoder.wx.module.iot.controller.admin.connector.vo.IotConnectorConnectionTestRespVO.Outcome;

/**
 * 后台 MQTT 连接探测器。
 *
 * <p>探测使用临时客户端和清洁会话，不取得 Redis/数据库运行租约，不写连接器状态，也不把收到的遥测交给入库链路。
 * 这样管理员测试不会抢占正式动态会话或制造业务事件。</p>
 */
public class IotMqttConnectivityTester {
    @FunctionalInterface
    interface ClientFactory {
        MqttAsyncClient create(String endpoint, String clientId) throws MqttException;
    }

    private final IotMqttCredentialsProvider credentialsProvider;
    private final ClientFactory clientFactory;

    public IotMqttConnectivityTester(IotMqttCredentialsProvider credentialsProvider) {
        this(credentialsProvider, (endpoint, clientId) ->
                new MqttAsyncClient(endpoint, clientId, new MemoryPersistence()));
    }

    IotMqttConnectivityTester(IotMqttCredentialsProvider credentialsProvider, ClientFactory clientFactory) {
        this.credentialsProvider = credentialsProvider;
        this.clientFactory = clientFactory;
    }

    public IotConnectorConnectionTestRespVO test(IotMqttSettings settings, Duration timeout) {
        long started = System.nanoTime();
        long timeoutMillis = timeout.toMillis();
        IotMqttSettings.Credentials credentials;
        try {
            credentials = credentialsProvider.resolve(settings);
        } catch (RuntimeException ex) {
            return result(settings, Outcome.CREDENTIALS_UNAVAILABLE, started, 0, "凭据引用不可用");
        }
        MqttAsyncClient client = null;
        boolean connected = false;
        try {
            // 客户端编号每次随机且使用 cleanSession，避免测试残留订阅和未确认积压。
            client = clientFactory.create(settings.endpoint(), "wx-iot-probe-" + UUID.randomUUID());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);
            int connectionSeconds = (int) Math.max(1, Math.min(15, (timeoutMillis + 999) / 1000));
            options.setConnectionTimeout(connectionSeconds);
            options.setKeepAliveInterval(Math.min(30, Math.max(5, connectionSeconds)));
            options.setHttpsHostnameVerificationEnabled(true);
            options.setUserName(credentials.username());
            options.setPassword(credentials.password());
            client.connect(options).waitForCompletion(timeoutMillis);
            connected = true;
            String[] topics = settings.topics().toArray(String[]::new);
            int[] qualities = new int[topics.length];
            Arrays.fill(qualities, 1);
            var subscribed = client.subscribe(topics, qualities);
            subscribed.waitForCompletion(timeoutMillis);
            int[] granted = subscribed.getGrantedQos();
            if (granted == null || granted.length != topics.length || Arrays.stream(granted).anyMatch(qos -> qos != 1)) {
                return result(settings, Outcome.SUBSCRIBE_FAILED, started, topics.length, "主题订阅未获得 QoS 1");
            }
            return result(settings, Outcome.SUCCEEDED, started, topics.length, "连接和主题订阅成功");
        } catch (MqttException ex) {
            Outcome outcome = isTimeout(ex) ? Outcome.TIMEOUT
                    : connected ? Outcome.SUBSCRIBE_FAILED : Outcome.CONNECT_FAILED;
            return result(settings, outcome, started, settings.topics().size(),
                    outcome == Outcome.TIMEOUT ? "连接测试超时" : connected ? "主题订阅失败" : "消息代理连接失败");
        } catch (RuntimeException ex) {
            return result(settings, Outcome.CONNECT_FAILED, started, settings.topics().size(), "连接测试失败");
        } finally {
            Arrays.fill(credentials.password(), '\0');
            close(client);
        }
    }

    private boolean isTimeout(MqttException exception) {
        return exception.getReasonCode() == MqttException.REASON_CODE_CLIENT_TIMEOUT
                || exception.getCause() instanceof java.net.SocketTimeoutException;
    }

    private void close(MqttAsyncClient client) {
        if (client == null) {
            return;
        }
        try {
            client.disconnectForcibly(1000, 1000, false);
        } catch (MqttException ignored) {
            // 探测结束必须继续释放客户端，不把底层地址或认证错误写入管理响应。
        } finally {
            try {
                client.close(true);
            } catch (MqttException ignored) {
                // 客户端已经关闭时无需再次处理。
            }
        }
    }

    private IotConnectorConnectionTestRespVO result(IotMqttSettings settings, Outcome outcome,
            long started, int topicCount, String message) {
        return new IotConnectorConnectionTestRespVO(settings.connectorId(), outcome,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), topicCount, message);
    }
}
