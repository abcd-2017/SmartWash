package com.smartwash;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.smartwash.mapper")
public class SmartWashApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWashApplication.class, args);
    }

}
