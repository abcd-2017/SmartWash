package com.smartwash.config;

import com.smartwash.common.log.AccessLogFilter;
import com.smartwash.common.log.RequestLogProperties;
import com.smartwash.common.log.TraceIdFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 日志基础设施过滤器注册。
 *
 * <p>过滤器执行顺序（order 越小越靠外层、越先执行）：
 * <pre>
 * TraceIdFilter(HIGHEST_PRECEDENCE)
 *   → springSecurityFilterChain(SecurityProperties.DEFAULT_FILTER_ORDER = -100)
 *     → JwtAuthenticationFilter（安全链内）
 *     → AccessLogFilter(-99，安全链内层)
 *       → DispatcherServlet
 * </pre>
 * <ul>
 *   <li>TraceIdFilter 必须是链条第一个执行的过滤器（早于安全过滤链与 JWT 过滤器），
 *       才能让包括 JWT 校验失败 warn 在内的全部日志行携带 request_id，故取
 *       {@link Ordered#HIGHEST_PRECEDENCE}；</li>
 *   <li>AccessLogFilter 必须位于安全过滤链【内层】（order 大于 -100）：安全链最外层的
 *       SecurityContextHolderFilter 会在链返回时清理 SecurityContext，只有内层过滤器
 *       在 finally 中仍能取到登录用户，用于访问日志的 userId 字段。</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(RequestLogProperties.class)
public class LoggingFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter traceIdFilter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(traceIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AccessLogFilter> accessLogFilterRegistration(AccessLogFilter accessLogFilter) {
        FilterRegistrationBean<AccessLogFilter> registration = new FilterRegistrationBean<>(accessLogFilter);
        // 必须大于 SecurityProperties.DEFAULT_FILTER_ORDER(-100)，即位于安全过滤链内层（见类注释）
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
