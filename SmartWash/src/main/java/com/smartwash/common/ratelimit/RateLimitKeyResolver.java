package com.smartwash.common.ratelimit;

import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流键客户端标识解析器。
 *
 * <p>解析优先级（fallback 链，任何一环失败自动降级，绝不阻塞业务）：
 * <ol>
 *   <li>注解配置 SpEL 且解析结果非空 —— SpEL 值（如从方法参数取手机号）；</li>
 *   <li>已登录用户（UserContextHolder 上下文）—— {@code user-{userId}}（防换参绕过）；</li>
 *   <li>未认证请求 —— 客户端 IP（可信代理规则见 {@link ClientIpResolver}）；</li>
 *   <li>非 Web 线程等极端场景 —— {@code unknown}（安全退化）。</li>
 * </ol>
 *
 * <p>SpEL 依赖编译期 {@code -parameters} 保留参数名（spring-boot-starter-parent 已默认开启）；
 * 解析异常/空值仅 warn 留痕并降级，限流永远是旁路组件。
 */
@Slf4j
public class RateLimitKeyResolver {

    /** 登录用户标识前缀：与 JWT sub 的 user- 前缀风格一致 */
    private static final String USER_IDENTITY_PREFIX = "user-";

    /** 兜底标识：无法解析任何维度时使用（计数退化为全局共享桶，宁可误聚不可失效） */
    private static final String UNKNOWN_IDENTITY = "unknown";

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final ClientIpResolver clientIpResolver;

    public RateLimitKeyResolver(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    /**
     * 解析客户端标识。
     *
     * @param method 目标方法（SpEL 参数名发现）
     * @param args   方法实际入参
     * @param spel   注解配置的 SpEL 表达式（可为空）
     * @return 客户端标识（手机号 / user-{userId} / IP / unknown）
     */
    public String resolveIdentity(Method method, Object[] args, String spel) {
        if (StringUtils.hasText(spel)) {
            String spelValue = evaluateSpel(method, args, spel);
            if (StringUtils.hasText(spelValue)) {
                return sanitize(spelValue);
            }
            log.debug("限流键 SpEL 解析结果为空，降级为登录用户/IP 维度, spel: {}", spel);
        }

        // 登录态请求优先按 userId：注解未配 SpEL 或 SpEL 空值时，防攻击者换参绕过
        LoginUser user = UserContextHolder.getUser();
        if (user != null && user.getUserId() != null) {
            return USER_IDENTITY_PREFIX + user.getUserId();
        }

        // 匿名请求按客户端 IP 兜底
        HttpServletRequest request = currentRequest();
        String ip = clientIpResolver.resolve(request);
        if (StringUtils.hasText(ip)) {
            return sanitize(ip);
        }
        return UNKNOWN_IDENTITY;
    }

    /** 执行 SpEL：以方法参数为上下文取值；异常时 warn 并返回 null（降级不阻塞） */
    private String evaluateSpel(Method method, Object[] args, String spel) {
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, args, PARAMETER_NAME_DISCOVERER);
            Object value = SPEL_PARSER.parseExpression(spel).getValue(context);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            log.warn("限流键 SpEL 解析失败，降级为登录用户/IP 维度, spel: {}, 原因: {}", spel, e.getMessage());
            return null;
        }
    }

    /**
     * 键净化：仅保留字母数字与常见安全符号，其余替换为下划线。
     * SpEL 值可能来自用户输入（如参数），防止任意字符进入 Redis key 造成键分裂/命名空间污染。
     */
    private String sanitize(String identity) {
        return identity.replaceAll("[^a-zA-Z0-9_@.\\-]", "_");
    }

    /** 获取当前请求（非 Web 线程返回 null） */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
