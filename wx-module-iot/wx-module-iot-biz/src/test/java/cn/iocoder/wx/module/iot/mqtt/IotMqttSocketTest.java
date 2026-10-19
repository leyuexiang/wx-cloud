package cn.iocoder.wx.module.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 通过回环 TCP 协议桩检查真实 Paho 客户端发送的确认帧，不连接任何外部 Broker。 */
class IotMqttSocketTest {
    @Test
    void shouldWaitForDurableReceptionBeforeSendingPuback() throws Exception {
        verifyWireAcknowledgement(false);
    }

    @Test
    void shouldDisconnectWithoutPubackWhenReceptionFails() throws Exception {
        verifyWireAcknowledgement(true);
    }

    private void verifyWireAcknowledgement(boolean fail) throws Exception {
        var received = new CountDownLatch(1);
        var finishReception = new CountDownLatch(1);
        var lease = mock(IotMqttLease.class);
        when(lease.isValid()).thenReturn(true);
        try (var broker = new LoopbackBroker()) {
            var connector = IotMqttSettingsTest.connector();
            connector.setEndpoint("tcp://127.0.0.1:" + broker.server.getLocalPort());
            var settings = IotMqttSettings.parse(connector, true, new ObjectMapper());
            try (var connection = new PahoIotMqttConnection(
                    new MqttAsyncClient(settings.endpoint(), settings.clientId(), new MemoryPersistence()), settings,
                    new IotMqttSettings.Credentials("test", "temporary".toCharArray()), lease, (topic, body) -> {
                        received.countDown();
                        try {
                            if (!finishReception.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("测试接收超时");
                            }
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("测试中断");
                        }
                        if (fail) {
                            throw new IllegalStateException("模拟持久接收失败");
                        }
                    })) {
                connection.connect();
                assertTrue(received.await(5, TimeUnit.SECONDS));
                assertTrue(broker.checkedNoEarlyAck.await(5, TimeUnit.SECONDS));
                finishReception.countDown();
                var acknowledgement = broker.result.get(8, TimeUnit.SECONDS);
                if (fail) {
                    assertNull(acknowledgement);
                } else {
                    assertEquals(4, acknowledgement.type());
                    assertArrayEquals(new byte[]{0, 42}, acknowledgement.body());
                }
            } finally {
                finishReception.countDown();
            }
        }
    }

    /** 只实现本测试使用的连接、订阅和 QoS 1 发送握手，不代替真实 Broker 兼容性验收。 */
    private static class LoopbackBroker implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final CountDownLatch checkedNoEarlyAck = new CountDownLatch(1);
        private final Future<Packet> result;

        LoopbackBroker() throws Exception {
            server.setSoTimeout(10000);
            result = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    var input = socket.getInputStream();
                    var output = socket.getOutputStream();
                    Packet connect = read(input);
                    assertEquals(1, connect.type());
                    assertEquals(0, connect.body()[7] & 2, "客户端必须使用持久会话");
                    output.write(new byte[]{0x20, 2, 0, 0});
                    output.flush();
                    Packet subscription = read(input);
                    assertEquals(8, subscription.type());
                    output.write(new byte[]{(byte) 0x90, 3, subscription.body()[0], subscription.body()[1], 1});
                    output.flush();
                    byte[] topic = "pilot/telemetry".getBytes(StandardCharsets.UTF_8);
                    var payload = new ByteArrayOutputStream();
                    payload.write(0);
                    payload.write(topic.length);
                    payload.write(topic);
                    payload.write(new byte[]{0, 42, '{', '}'});
                    output.write(0x32);
                    output.write(payload.size());
                    output.write(payload.toByteArray());
                    output.flush();
                    socket.setSoTimeout(250);
                    assertThrows(SocketTimeoutException.class, () -> read(input), "接收未完成前不得确认");
                    checkedNoEarlyAck.countDown();
                    socket.setSoTimeout(8000);
                    try {
                        return read(input);
                    } catch (EOFException ex) {
                        return null;
                    }
                } finally {
                    checkedNoEarlyAck.countDown();
                }
            });
        }

        private static Packet read(InputStream input) throws Exception {
            int header = input.read();
            if (header < 0) {
                throw new EOFException();
            }
            int length = 0;
            int multiplier = 1;
            int encoded;
            do {
                encoded = input.read();
                if (encoded < 0 || multiplier > 128 * 128 * 128) {
                    throw new EOFException();
                }
                length += (encoded & 127) * multiplier;
                multiplier *= 128;
            } while ((encoded & 128) != 0);
            if (length > 65536) {
                throw new IllegalStateException("测试协议帧过大");
            }
            byte[] body = input.readNBytes(length);
            if (body.length != length) {
                throw new EOFException();
            }
            return new Packet(header >> 4, body);
        }

        @Override
        public void close() throws Exception {
            server.close();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private record Packet(int type, byte[] body) { }
}
