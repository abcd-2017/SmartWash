package com.smartwash.common.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 进程内存滑动窗口限流计数器（测试回退 / 无 Redis 单实例部署）。
 *
 * <p>语义与 {@link RedisRateLimitChecker} 完全一致（窗口按请求时间戳精确滑动、失败请求不占额度），
 * 差异仅在存储介质：计数在 JVM 内存中，多实例部署时各实例独立计数（实际配额按实例数放大），
 * 仅适用于单实例场景或单测。
 *
 * <p>线程安全：每 key 一把「时间戳队列锁」，窗口清理与计数在同一临界区完成，check-and-increment 原子。
 * 内存量级假设：活跃 key 数 = 窗口内出现过的客户端标识数（校园场景有限），过期 key 在下次访问时
 * 惰性清空，不做后台清扫。
 */
public class InMemoryRateLimitChecker implements RateLimitChecker {

    /** 每个限流键的请求时间戳队列（队首最旧） */
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /** 毫秒时钟（可注入以便测试窗口滚动，默认系统时钟） */
    private final LongSupplier clock;

    public InMemoryRateLimitChecker() {
        this(() -> System.currentTimeMillis());
    }

    /** 测试用：注入可控时钟，验证窗口滑动语义 */
    public InMemoryRateLimitChecker(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, long windowMillis, long limit) {
        long now = clock.getAsLong();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            // 1. 滑出清理：队首早于窗口起点的记录逐个移除
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            // 2. 阈值判断：失败请求不占额度（与 Redis 版语义一致）
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
