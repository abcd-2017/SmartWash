package com.smartwash.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪过滤器：为每个请求生成 32 位去横线 traceId，写入 MDC（key=traceId）并回写响应头 X-Trace-Id。
 *
 * <p>注册顺序见 {@link com.smartwash.config.LoggingFilterConfig}：以 HIGHEST_PRECEDENCE 注册为
 * 最外层过滤器，早于 springSecurityFilterChain（-100）与 JWT 过滤器执行，保证业务日志全部携带 traceId，
 * 一个请求的所有日志行可按 traceId 串联。logback 两个 appender 的 pattern 以 %X{traceId} 输出。
 *
 * <p>请求结束后 finally 中清理 MDC，防止容器线程复用导致的 traceId 串扰；异步线程（@Async、
 * 自建线程池）的 MDC 传播由 {@link com.smartwash.config.MdcTaskDecorator} 负责。
 *
 * <p>traceId 为随机 UUID，不含任何业务/身份信息，可安全透出给客户端用于排障回传。
 */
@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 中 traceId 的 key，logback pattern 以 %X{traceId} 引用 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 回写给调用方的响应头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 32 位去横线 UUID，作为本次请求的全链路日志标识
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID_KEY, traceId);
        try {
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            // 容器线程复用，必须清理，避免 traceId 泄漏到下一个请求
            MDC.clear();
        }
    }
}
