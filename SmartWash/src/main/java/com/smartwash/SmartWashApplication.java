package com.smartwash;

import org.mybatis.spring.annotation.MapperScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@MapperScan({"com.smartwash.mapper", "com.smartwash.divination.mapper", "com.smartwash.toolbox.mapper", "com.smartwash.toolbox.delivery.mapper", "com.smartwash.workbench.mapper", "com.smartwash.game.mapper", "com.smartwash.mood.mapper", "com.smartwash.review.mapper", "com.smartwash.subscription.mapper", "com.smartwash.common.audit.mapper", "com.smartwash.points.mapper", "com.smartwash.notice.mapper"})
@EnableScheduling
@EnableAsync
public class SmartWashApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWashApplication.class, args);
        log.info("SmartWash 应用启动成功");
    }

}
