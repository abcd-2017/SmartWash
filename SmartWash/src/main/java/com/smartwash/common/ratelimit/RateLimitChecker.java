package com.smartwash.common.ratelimit;

/**
 * 限流计数器接口：滑动窗口「try-acquire」语义的抽象。
 *
 * <p>实现方：
 * <ul>
 *   <li>{@link RedisRateLimitChecker} —— Redis + Lua 原子滑动窗口（生产默认，多实例共享配额）；</li>
 *   <li>{@link InMemoryRateLimitChecker} —— 进程内存滑动窗口（单实例/测试回退）。</li>
 * </ul>
 */
public interface RateLimitChecker {

    /**
     * 尝试在指定滑动窗口内获取一次访问额度。
     *
     * @param key          限流键（已含规则命名空间与客户端标识）
     * @param windowMillis 窗口时长（毫秒）
     * @param limit        窗口内最大次数
     * @return true=放行（额度内）；false=超限（应拒绝）
     */
    boolean tryAcquire(String key, long windowMillis, long limit);
}
