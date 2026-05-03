package com.smartwash;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.smartwash.mapper")
@EnableScheduling
public class SmartWashApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWashApplication.class, args);
    }

}
