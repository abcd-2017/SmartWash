package com.smartwash.common.log;

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
import java.util.regex.Pattern;

/**
 * 链路追踪过滤器：为每个请求确定 request_id，写入 MDC（key={@link #REQUEST_ID_KEY}）并回写
 * 响应头 {@link #REQUEST_ID_HEADER}。
 *
 * <p><b>request_id 来源决策</b>（见 {@link #resolveRequestId}）：
 * <ol>
 *   <li>优先复用上游 {@code X-Request-Id} 透传值（网关/压测/前端埋点串联场景），
 *       但必须通过格式校验——白名单字符集 {@code [A-Za-z0-9_-]}、长度 8~64，
 *       杜绝换行/控制字符/引号进入日志造成<b>日志注入</b>；</li>
 *   <li>上游缺失或非法时生成 32 位去横线 UUID（短格式，hex 小写，天然可回传下游继续透传）。</li>
 * </ol>
 *
 * <p><b>注册顺序</b>见 {@link com.smartwash.config.LoggingFilterConfig}：以 HIGHEST_PRECEDENCE 注册为
 * 最外层过滤器，早于 springSecurityFilterChain（-100）与其中的 JwtAuthenticationFilter 执行——
 * 保证包括 JWT 校验失败 warn 在内的所有业务日志都携带 request_id。logback 两个 appender 的
 * pattern 以 {@code %X{request_id}} 输出。
 *
 * <p>请求结束后 finally 中清理 MDC，防止容器线程复用导致串扰；异步线程的 MDC 传播由
 * {@link com.smartwash.config.MdcTaskDecorator} 负责。request_id 为随机值，不含任何业务/身份信息，
 * 可安全透出给客户端用于排障回传。
 */
@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 中 request_id 的 key，logback pattern 以 %X{request_id} 引用 */
    public static final String REQUEST_ID_KEY = "request_id";

    /** 上游透传与响应回写的头名称 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 上游透传值白名单：字母数字与 -/_，长度 8~64（满足常见 UUID/网关 id 形态，拒日志注入字符） */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String upstreamId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = resolveRequestId(upstreamId);
        MDC.put(REQUEST_ID_KEY, requestId);
        try {
            if (upstreamId != null && !requestId.equals(upstreamId.trim())) {
                // 上游值被判定非法（防日志注入），记录 debug 便于排查透传方配置问题
                log.debug("上游 X-Request-Id 未通过格式校验，已丢弃并生成新 request_id");
            }
            // 回写响应头，调用方/网关可在响应中拿到本次请求的排障 id
            response.setHeader(REQUEST_ID_HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            // 容器线程复用，必须清理，避免 request_id 泄漏到下一个请求
            MDC.clear();
        }
    }

    /**
     * request_id 决策（纯函数，可静态单测）：上游值合法则复用，否则生成 32 位去横线 UUID。
     *
     * @param upstreamId 上游 X-Request-Id 头值（可为 null）
     * @return 本次请求使用的 request_id
     */
    public static String resolveRequestId(String upstreamId) {
        if (upstreamId != null) {
            String trimmed = upstreamId.trim();
            if (SAFE_REQUEST_ID.matcher(trimmed).matches()) {
                return trimmed;
            }
        }
        return newRequestId();
    }

    /** 判断透传值是否合法（trim 后白名单校验） */
    public static boolean isValidRequestId(String requestId) {
        return requestId != null && SAFE_REQUEST_ID.matcher(requestId.trim()).matches();
    }

    /** 生成 32 位去横线小写 hex（UUID 短格式），可安全作为下游 X-Request-Id 继续透传 */
    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
