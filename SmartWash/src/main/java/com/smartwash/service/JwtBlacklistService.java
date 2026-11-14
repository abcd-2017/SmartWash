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

    /** 改密会话失效标记 key 前缀：jwt:session-invalid:{subject}，值为改密时间戳（epoch 毫秒） */
    private static final String SESSION_INVALIDATE_PREFIX = "jwt:session-invalid:";
    /**
     * 标记保留时长：8 天。refresh 令牌最长寿命 7 天，超过该时长后所有早于改密时间签发的
     * token 已自然过期，标记即可安全清理；同时多实例共享 Redis，天然支持分布式部署。
     */
    private static final long SESSION_INVALIDATE_TTL_DAYS = 8;

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

    /**
     * 标记某主体（access/refresh 共用的 sub，如 admin-{用户名}）的全部既有会话失效。
     *
     * <p>用于管理员改密场景：refresh 令牌一次性轮换、签发时未持久化 jti，无法按 jti 批量拉黑；
     * 改以「改密时间戳」为分界——过滤器与刷新端点比对 token 的 iat，早于该时间戳即拒绝。
     * 只影响该主体，不影响其他管理员。</p>
     *
     * @param subject JWT sub（含 admin-/user- 前缀）
     */
    public void invalidateUserSessions(String subject) {
        if (subject == null || subject.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(SESSION_INVALIDATE_PREFIX + subject,
                String.valueOf(System.currentTimeMillis()), SESSION_INVALIDATE_TTL_DAYS, TimeUnit.DAYS);
        log.info("用户会话已标记失效（改密）, subject: {}", subject);
    }

    /**
     * 判断 token 是否因改密而失效：token 签发时间早于该主体最近一次改密时间即视为旧会话。
     *
     * @param subject        JWT sub（含 admin-/user- 前缀）
     * @param issuedAtMillis token 的 iat（epoch 毫秒）；非正数表示无法判断，按未失效处理
     * @return true=旧会话应拒绝
     */
    public boolean isSessionInvalidated(String subject, long issuedAtMillis) {
        if (subject == null || subject.isBlank() || issuedAtMillis <= 0) {
            return false;
        }
        try {
            String invalidatedAt = redisTemplate.opsForValue().get(SESSION_INVALIDATE_PREFIX + subject);
            if (invalidatedAt == null || invalidatedAt.isBlank()) {
                return false;
            }
            return issuedAtMillis < Long.parseLong(invalidatedAt.trim());
        } catch (Exception e) {
            // Redis 故障按 fail-open 放行（与黑名单既有口径一致）：最坏后果是旧会话存活至自然过期，
            // 不因 Redis 抖动拒绝合法请求
            log.warn("改密会话失效标记读取失败，按 fail-open 放行, subject: {}", subject, e);
            return false;
        }
    }
}
