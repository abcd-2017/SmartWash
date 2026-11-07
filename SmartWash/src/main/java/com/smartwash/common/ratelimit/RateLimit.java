package com.smartwash.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式限流方法注解（Redis + Lua 滑动窗口，多实例共享配额）。
 *
 * <p>使用方式：标注在 controller 方法上，超限请求由 {@link RateLimitAspect} 拦截并抛出
 * {@link RateLimitExceededException}，经全局异常处理按消费方类型返回统一失败响应。
 *
 * <p>限流键解析优先级（见 {@link RateLimitKeyResolver}）：
 * <ol>
 *   <li>注解 {@link #key()} 配置 SpEL 表达式且解析结果非空 —— 以 SpEL 值作为客户端标识（如从方法参数取手机号）；</li>
 *   <li>SpEL 为空或解析为空值 —— 已登录用户取 userId（防换参绕过）；</li>
 *   <li>未认证请求 —— 按客户端 IP 兜底（可信代理规则与登录风控一致）。</li>
 * </ol>
 *
 * <p>示例：
 * <pre>{@code
 * // 按手机号参数限流：60 秒窗口内最多 10 次
 * @RateLimit(key = "#userLoginFrom.phoneNumber", window = 60, limit = 10)
 * @PostMapping("/user/login")
 * public Result<LoginVo> login(@RequestBody @Valid UserLoginFrom userLoginFrom, ...) {...}
 *
 * // 未认证接口不配 key：自动按 IP 兜底
 * @RateLimit(window = 60, limit = 30)
 * @GetMapping("/{code}")
 * public ResponseEntity<Void> redirect(...) {...}
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流规则名，用于 Redis key 命名空间区分不同规则。
     * 留空时默认取「类简名#方法名」，重载方法请显式命名避免键冲突。
     */
    String name() default "";

    /**
     * SpEL 表达式，从方法参数解析客户端标识（如 {@code "#userLoginFrom.phoneNumber"}）。
     * 解析失败或结果为空时自动降级为「登录用户 userId / 客户端 IP」，不会阻塞业务。
     * 依赖编译期 -parameters 保留参数名（spring-boot-starter-parent 已默认开启）。
     */
    String key() default "";

    /** 限流窗口时长（秒），与 {@link #limit()} 组成「window 秒内最多 limit 次」语义 */
    int window() default 60;

    /** 限流窗口内允许的最大请求次数 */
    int limit() default 10;

    /** 超限响应文案（透传到统一响应 message，三端按业务失败展示，无需新增错误码） */
    String message() default "请求过于频繁，请稍后再试";
}
