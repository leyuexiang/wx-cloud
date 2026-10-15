package cn.iocoder.jtyf.module.vegetation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 植被业务编排服务启动类；高耗时地块计算由Python生成器负责。 */
@SpringBootApplication
public class VegetationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(VegetationServerApplication.class, args);
    }
}
