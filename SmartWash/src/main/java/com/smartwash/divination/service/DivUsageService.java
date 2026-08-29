package com.smartwash.divination.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 限流计数/用量统计服务。
 *
 * Redis 键设计（db 3，沿用现有实例）：
 *   div:rl:u:{userId}:{yyyyMMdd}  每用户每日解读次数（TTL 25h）
 *   div:rl:ip:{ip}:{yyyyMMdd}     每 IP 每日次数（TTL 25h）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DivUsageService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_USER_LIMIT = "div:rl:u:%d:%s";
    private static final String KEY_IP_LIMIT = "div:rl:ip:%s:%s";
    private static final Duration TTL = Duration.ofHours(25);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 检查并递增用户每日解读次数。
     *
     * @param userId    用户 ID
     * @param dailyLimit 每日限额
     * @return true=未超限（已递增）；false=超限
     */
    public boolean tryConsumeUserLimit(Long userId, int dailyLimit) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = String.format(KEY_USER_LIMIT, userId, date);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, TTL);
        }
        return count != null && count <= dailyLimit;
    }

    /**
     * 检查并递增 IP 每日解读次数。
     */
    public boolean tryConsumeIpLimit(String ip, int dailyLimit) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = String.format(KEY_IP_LIMIT, ip, date);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, TTL);
        }
        return count != null && count <= dailyLimit;
    }

    /**
     * 获取用户当日已用次数。
     */
    public long getUserUsedCount(Long userId) {
        String date = LocalDate.now().format(DATE_FMT);
        String key = String.format(KEY_USER_LIMIT, userId, date);
        String val = redisTemplate.opsForValue().get(key);
        return val == null ? 0 : Long.parseLong(val);
    }
}
