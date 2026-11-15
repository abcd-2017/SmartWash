package com.smartwash.common.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 客户端平台上下文过滤器：解析统一请求头（X-Platform / X-App-Version / X-Network-Type / User-Agent），
 * 产出不可变 {@link ClientContext} 并写入 {@link ClientContextHolder}（ThreadLocal + MDC）。
 *
 * <p><b>职责边界</b>：只解析、只赋值，不校验、不拦截。任何缺失/非法头均退化为默认值，
 * 绝不抛异常阻断请求（平台标识是旁路信息，不能成为可用性短板）。
 *
 * <p><b>注册顺序</b>见 {@link com.smartwash.config.LoggingFilterConfig}：紧跟 TraceIdFilter 之后、
 * springSecurityFilterChain 之前（order = HIGHEST_PRECEDENCE + 1），保证：
 * <ul>
 *   <li>request_id 已就绪，平台上下文相关日志同样携带链路 id；</li>
 *   <li>安全链内（含登录接口）的业务代码与日志都能读到平台上下文。</li>
 * </ul>
 *
 * <p>请求结束（含下游异常）在 finally 中清理 ThreadLocal/MDC，防止容器线程复用串扰。
 */
@Component
public class ClientContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ClientContext context = ClientContext.fromHeaders(
                request.getHeader(ClientContext.HEADER_PLATFORM),
                request.getHeader(ClientContext.HEADER_APP_VERSION),
                request.getHeader(ClientContext.HEADER_NETWORK_TYPE),
                request.getHeader(ClientContext.HEADER_USER_AGENT));
        ClientContextHolder.set(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            ClientContextHolder.clear();
        }
    }
}
