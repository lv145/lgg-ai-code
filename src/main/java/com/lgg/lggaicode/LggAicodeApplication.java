package com.lgg.lggaicode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.lgg.lggaicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)

public class LggAicodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LggAicodeApplication.class, args);
    }

}
