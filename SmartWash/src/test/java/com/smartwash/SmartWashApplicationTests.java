package com.smartwash;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载冒烟测试。
 * 使用 test profile：H2 内存库 + 惰性 Redis/MinIO 配置，不依赖真实 MySQL/Redis（见 application-test.yaml）。
 */
@SpringBootTest
@ActiveProfiles("test")
class SmartWashApplicationTests {

    @Test
    void contextLoads() {
    }

}
