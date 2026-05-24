package com.smartwash.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单服务 —— 将已登出的 token jti 存入 Redis，使其失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

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
}
