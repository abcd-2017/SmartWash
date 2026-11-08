package com.smartwash.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * {@link RateLimit} 注解切面：方法级分布式限流入口。
 *
 * <p>流程：解析限流键（SpEL/用户/IP，见 {@link RateLimitKeyResolver}）→ 计数器原子 try-acquire
 * （{@link RateLimitChecker}，Redis Lua 或内存版）→ 额度内放行执行目标方法，超限抛
 * {@link RateLimitExceededException}（目标方法不执行）。
 *
 * <p>消费方语义：返回类型为 {@link ResponseEntity} 的接口（如短链跳转）按浏览器语义处理（HTTP 429），
 * 其余按 JSON 信封语义处理（HTTP 200 + 业务码 201），由全局异常处理器落地。
 *
 * <p>装配开关：{@code smartwash.rate-limit.enabled=false} 时本切面不注册（见 RateLimitConfig）。
 */
@Slf4j
@Aspect
public class RateLimitAspect {

    /** Redis 键命名空间前缀：与 RedisCacheManager 的 smartwash: 前缀风格一致，便于运维检索 */
    static final String KEY_PREFIX = "smartwash:rate-limit:";

    private final RateLimitChecker checker;
    private final RateLimitKeyResolver keyResolver;

    public RateLimitAspect(RateLimitChecker checker, RateLimitKeyResolver keyResolver) {
        this.checker = checker;
        this.keyResolver = keyResolver;
    }

    /** 注解拦截：绑定方法上的 @RateLimit 注解实例 */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String ruleName = StringUtils.hasText(rateLimit.name())
                ? rateLimit.name()
                : method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        String identity = keyResolver.resolveIdentity(method, pjp.getArgs(), rateLimit.key());
        String key = KEY_PREFIX + ruleName + ":" + identity;
        long windowMillis = rateLimit.window() * 1000L;

        boolean allowed = checker.tryAcquire(key, windowMillis, rateLimit.limit());
        if (!allowed) {
            boolean browserFacing = ResponseEntity.class.isAssignableFrom(method.getReturnType());
            // 超限 warn 留痕：规则/维度/阈值齐备，供排查误限与识别刷量
            log.warn("触发接口限流, 规则: {}, 窗口: {}s, 阈值: {} 次, 客户端标识: {}, 消费方: {}",
                    ruleName, rateLimit.window(), rateLimit.limit(), identity,
                    browserFacing ? "浏览器(429)" : "JSON(201)");
            throw new RateLimitExceededException(rateLimit.message(), browserFacing);
        }
        return pjp.proceed();
    }
}
