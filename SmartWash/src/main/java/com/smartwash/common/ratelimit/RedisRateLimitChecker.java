package com.smartwash.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

/**
 * Redis + Lua 原子滑动窗口限流计数器（生产默认实现）。
 *
 * <p>算法：ZSet 按成员记录请求时间戳，Lua 脚本内「清理窗口外成员 → ZCARD 统计 → 未达阈值则 ZADD」
 * 一次执行，check-and-increment 原子化，消除「先读后写」在并发下的超放竞态（多实例共享配额）。
 *
 * <p><b>fail-open</b>：Redis 不可用（连接失败/超时/集群异常等）时降级放行并打 warn 告警——
 * 限流是保护性组件，不能因自身故障演变为全站拒绝服务；故障期间的刷量风险由 warn 日志暴露供人工介入。
 *
 * <p>成员唯一性：时间戳 + UUID，同毫秒并发请求不会互相覆盖；键 TTL 与窗口等长（PEXPIRE 每次续期），
 * 滑出窗口后键自动过期，无需后台清理任务。
 */
@Slf4j
public class RedisRateLimitChecker implements RateLimitChecker {

    /**
     * 滑动窗口 Lua 脚本：
     * KEYS[1] 限流键；ARGV[1] 窗口毫秒；ARGV[2] 窗口内最大次数；ARGV[3] 当前毫秒时间戳；ARGV[4] 唯一成员值。
     * 返回 1=放行（本次已计入窗口）；0=超限拒绝（窗口计数未被修改）。
     */
    private static final RedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            -- 清理滑出窗口的旧成员（score 时间戳早于 窗口起点 的全部移除）
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3] - ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            -- 已达阈值：拒绝且不写入，保证「失败请求不占额度」
            if count >= tonumber(ARGV[2]) then
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
            -- 键 TTL 与窗口等长：窗口内无请求后自动过期
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitChecker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, long windowMillis, long limit) {
        long now = System.currentTimeMillis();
        // 成员 = 时间戳 + 随机后缀：同毫秒并发请求成员互不覆盖
        String member = now + "-" + UUID.randomUUID();
        try {
            Long result = redisTemplate.execute(SLIDING_WINDOW_SCRIPT, List.of(key),
                    String.valueOf(windowMillis), String.valueOf(limit), String.valueOf(now), member);
            // 正常执行必返回 0/1；防御 null（如 pipeline 语义下）按拒绝处理（fail-safe，宁可错杀不放过刷量）
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("限流 Redis 异常，已降级放行（fail-open），请尽快检查 Redis 可用性, key: {}, 原因: {}",
                    key, e.getMessage());
            return true;
        }
    }
}
