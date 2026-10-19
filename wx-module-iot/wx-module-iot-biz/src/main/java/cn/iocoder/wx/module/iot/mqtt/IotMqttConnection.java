package cn.iocoder.wx.module.iot.mqtt;

/** 将网络客户端隔离于运行时编排，便于验证连接失败、确认和资源回收。 */
public interface IotMqttConnection extends AutoCloseable {
    void connect() throws Exception;
    boolean isConnected();
    @Override
    void close();

    interface Factory {
        IotMqttConnection create(IotMqttSettings settings, IotMqttLease lease);
    }
}
