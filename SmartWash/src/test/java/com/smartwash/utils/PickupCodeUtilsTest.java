package com.smartwash.utils;

import com.smartwash.exception.CustomExceptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取件码生成工具单测（评审报告后端 #40）：不依赖真实 MySQL/Redis，
 * 查重函数用内存计数模拟"库内 pickup_code 冲突"，验证冲突重试与最终成功语义。
 */
@DisplayName("PickupCodeUtils 取件码生成：格式契约与冲突重试")
class PickupCodeUtilsTest {

    @Test
    @DisplayName("冲突重试后最终成功：前两次查重命中，第三次生成成功，返回三段冒号格式的 6 位纯随机码")
    void generate_retryThenSuccess() {
        AtomicInteger checkCalls = new AtomicInteger();
        // 前 2 次查重返回冲突（已存在），第 3 次放行
        String code = PickupCodeUtils.generate(7L, 8L, candidate -> checkCalls.incrementAndGet() <= 2);

        assertEquals(3, checkCalls.get(), "前 2 次查重冲突，第 3 次必须生成成功，总查重次数应为 3");
        assertTrue(code.matches("^7:8:\\d{6}$"),
                "取件码必须保持 userId:orderId:6位随机数字 的三段冒号契约（Android/鸿蒙按冒号分段解析）");
    }

    @Test
    @DisplayName("冲突超限：持续冲突达到重试上限（首次 + 最多 3 次重试）后抛业务异常")
    void generate_exhaustedRetries_throws() {
        AtomicInteger checkCalls = new AtomicInteger();
        // 查重恒返回冲突，模拟唯一索引持续命中
        CustomExceptions ex = assertThrows(CustomExceptions.class,
                () -> PickupCodeUtils.generate(7L, 8L, candidate -> {
                    checkCalls.incrementAndGet();
                    return true;
                }));

        assertEquals("取件码生成失败，请稍后重试", ex.getMessage());
        assertEquals(PickupCodeUtils.MAX_RETRY + 1, checkCalls.get(),
                "首次生成 + 最多 3 次重试后必须放弃，不得无限循环");
    }

    @Test
    @DisplayName("首次生成即无冲突：查重仅调用 1 次并直接返回")
    void generate_noConflict_singleCheck() {
        AtomicInteger checkCalls = new AtomicInteger();
        String code = PickupCodeUtils.generate(1L, 2L, candidate -> {
            checkCalls.incrementAndGet();
            return false;
        });

        assertEquals(1, checkCalls.get(), "无冲突时应只查重 1 次");
        assertTrue(code.matches("^1:2:\\d{6}$"), "取件码随机段必须为 6 位纯数字");
    }
}
