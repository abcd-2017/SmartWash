package com.smartwash.common.ratelimit;

import lombok.Getter;

/**
 * 触发限流异常：由 {@link RateLimitAspect} 抛出，经全局异常处理器（ExceptionControllerAdvice）
 * 按消费方类型返回统一失败响应。
 *
 * <p>响应约定（不改既有接口契约、不新增三端错误码）：
 * <ul>
 *   <li>JSON 消费方（Android/鸿蒙/Web）：HTTP 200 + 统一 Result 信封 code=201（FAIL）+ message，
 *       三端拦截器按既有业务失败分支透传文案，无需任何端上改动；</li>
 *   <li>浏览器消费方（短链跳转等返回 {@code ResponseEntity} 的非信封接口）：HTTP 429 + 提示文案，
 *       与该类接口「HTTP 状态码即语义」的既有设计（302/404/410）一致。</li>
 * </ul>
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /** 浏览器消费方标记：true 时异常处理器返回 HTTP 429（非信封接口） */
    private final boolean browserFacing;

    public RateLimitExceededException(String message, boolean browserFacing) {
        super(message);
        this.browserFacing = browserFacing;
    }
}
