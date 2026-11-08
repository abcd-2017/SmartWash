package com.smartwash.common.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流组件装配。
 *
 * <p>开关与存储切换（application.yaml）：
 * <pre>
 * smartwash:
 *   rate-limit:
 *     enabled: true   # 总开关，false 时切面不装配，所有 @RateLimit 注解不生效
 *     store: redis    # redis=分布式滑动窗口（多实例共享配额，生产默认）；memory=进程内存版（单实例/无 Redis 回退）
 * </pre>
 */
@Configuration
public class RateLimitConfig {

    /** 限流切面：总开关关闭时不注册，注解退化为无操作 */
    @Bean
    @ConditionalOnProperty(prefix = "smartwash.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect rateLimitAspect(RateLimitChecker checker, RateLimitKeyResolver rateLimitKeyResolver) {
        return new RateLimitAspect(checker, rateLimitKeyResolver);
    }

    /** Redis Lua 滑动窗口计数器（生产默认）：多实例共享配额 */
    @Bean
    @ConditionalOnProperty(prefix = "smartwash.rate-limit", name = "store", havingValue = "redis", matchIfMissing = true)
    public RateLimitChecker redisRateLimitChecker(StringRedisTemplate stringRedisTemplate) {
        return new RedisRateLimitChecker(stringRedisTemplate);
    }

    /** 进程内存滑动窗口计数器：无 Redis 依赖的单实例回退 / 测试 */
    @Bean
    @ConditionalOnProperty(prefix = "smartwash.rate-limit", name = "store", havingValue = "memory")
    public RateLimitChecker inMemoryRateLimitChecker() {
        return new InMemoryRateLimitChecker();
    }

    /** 限流键解析器：SpEL -> 登录用户 -> IP 的 fallback 链 */
    @Bean
    public RateLimitKeyResolver rateLimitKeyResolver(ClientIpResolver clientIpResolver) {
        return new RateLimitKeyResolver(clientIpResolver);
    }

    /** 客户端 IP 解析器：与登录风控共用 security.trusted-proxy-cidrs 可信代理配置 */
    @Bean
    public ClientIpResolver clientIpResolver(
            @Value("${security.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        return new ClientIpResolver(trustedProxyCidrs);
    }
}
