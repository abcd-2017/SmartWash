package com.smartwash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单服务 —— 将已登出的 token jti 存入 Redis，使其失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    /**
     * 刷新令牌原子「检查+占用」脚本（Lua 由 Redis 单线程执行，天然原子）：
     * {@code SET jwt:blacklist:{jti} 1 NX EX {ttl}} —— 占用即拉黑一步完成，
     * key 不存在则写入并返回 1（抢到，本次刷新有效），已存在返回 0（已被消费/登出，按重放拦截）。
     * 用 DefaultRedisScript 单例承载：首次以 EVALSHA 执行后按 SHA1 缓存脚本摘要，
     * 后续仅发送摘要与参数，不重复传输完整脚本文本。
     */
    private static final DefaultRedisScript<Long> REFRESH_ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('set', KEYS[1], '1', 'NX', 'EX', ARGV[1]) then return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 将 token 的 jti 加入黑名单
     *
     * @param jti         JWT ID
     * @param ttlSeconds  剩余有效期（秒），过期后自动从 Redis 清除
     */
    public void blacklist(String jti, long ttlSeconds) {
        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.info("JWT 已加入黑名单, jti: {}, ttl: {}s", jti, ttlSeconds);
    }

    /**
     * 检查 jti 是否在黑名单中
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

    /**
     * 刷新令牌原子「检查+占用」——/auth/refresh 一次性轮换的防重放闸门
     * （替代原「查黑名单 → 重签 → 拉黑」两段式操作，封死并发重放同一 refreshToken 双胜出的竞态窗口，
     * 见 docs/adr/0007-JWT黑名单与auth-refresh会话策略.md 取舍注记 (a)）。
     * <p>语义：Lua {@code SET jwt:blacklist:{jti} 1 NX EX ttl}，占用即拉黑一步完成——
     * 返回 true 表示抢到（旧 jti 已在重签前拉黑，可继续重签新 token）；
     * 返回 false 表示该 jti 已被并发消费或已登出（调用方按重放处理，返回 401 信封）。
     *
     * @param jti        refresh 令牌 JWT ID；为空时无占用对象直接放行（与原「jti != null 才拉黑」判空语义一致）
     * @param ttlSeconds 占用时长 = refresh 剩余有效期（秒），到期自动从 Redis 清除；非正数同样直接放行
     * @return true=抢到占用（继续重签）；false=已被占用（重放拦截）
     */
    public boolean tryAcquireRefresh(String jti, long ttlSeconds) {
        // 无 jti 或已无剩余有效期的令牌没有可占用的键，沿用原拉黑判空语义直接放行（签名/过期校验由控制器前置环节兜底）
        if (jti == null || ttlSeconds <= 0) {
            return true;
        }
        String key = BLACKLIST_PREFIX + jti;
        try {
            Long acquired = redisTemplate.execute(REFRESH_ACQUIRE_SCRIPT,
                    Collections.singletonList(key), String.valueOf(ttlSeconds));
            return Long.valueOf(1L).equals(acquired);
        } catch (Exception e) {
            // Redis 故障降级：与黑名单既有 fail-open 口径一致（ADR-0007 修订第 8 条）——放行刷新，
            // 轮换拉黑随故障失效（旧 token 保留至自然过期），不因 Redis 抖动拒绝合法续期
            log.warn("刷新令牌原子占用脚本执行失败，按 fail-open 放行, jti: {}", jti, e);
            return true;
        }
    }
}
