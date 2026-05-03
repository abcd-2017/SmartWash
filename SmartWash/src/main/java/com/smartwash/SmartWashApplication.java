package com.smartwash;

import org.mybatis.spring.annotation.MapperScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@MapperScan("com.smartwash.mapper")
@EnableScheduling
public class SmartWashApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWashApplication.class, args);
        log.info("SmartWash 应用启动成功");
    }

}
