package cn.iocoder.wx.module.iot.mqtt;

/** 凭据解析与会话生命周期解耦；当前实现仍只读取部署时注入的环境变量。 */
@FunctionalInterface
public interface IotMqttCredentialsProvider {
    IotMqttSettings.Credentials resolve(IotMqttSettings settings);
}
