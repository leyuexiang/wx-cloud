package cn.iocoder.wx.module.semi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource({"classpath*:applicationContext.xml"})
public class WxModuleSemiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WxModuleSemiApplication.class, args);
    }

}
