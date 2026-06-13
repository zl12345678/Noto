package com.noto.zhihui;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.noto.zhihui.mapper")
@EnableScheduling
public class ZhihuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhihuiApplication.class, args);
    }
}
