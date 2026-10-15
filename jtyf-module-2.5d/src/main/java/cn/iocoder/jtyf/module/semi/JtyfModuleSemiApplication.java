package cn.iocoder.jtyf.module.semi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ImportResource({"classpath*:applicationContext.xml"})
public class JtyfModuleSemiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JtyfModuleSemiApplication.class, args);
    }

}
