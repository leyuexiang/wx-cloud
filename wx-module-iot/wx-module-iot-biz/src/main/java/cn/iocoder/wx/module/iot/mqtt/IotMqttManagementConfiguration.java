package cn.iocoder.wx.module.iot.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 管理端结构校验和连接测试所需的轻量 MQTT 组件，不创建网络客户端或运行租约。 */
@Configuration(proxyBeanMethods = false)
public class IotMqttManagementConfiguration {
    @Bean
    @ConditionalOnMissingBean(IotMqttCredentialsProvider.class)
    public IotMqttCredentialsProvider managementCredentialsProvider(ObjectMapper mapper) {
        return settings -> settings.credentials(System::getenv, mapper);
    }

    @Bean
    public IotMqttConnectivityTester iotMqttConnectivityTester(IotMqttCredentialsProvider provider) {
        return new IotMqttConnectivityTester(provider);
    }
}
