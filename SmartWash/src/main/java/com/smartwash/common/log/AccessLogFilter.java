package com.smartwash.common.log;

import com.smartwash.utils.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 请求出入日志过滤器：请求完成时打一条单行日志，内容仅含 method、path、status、耗时、userId。
 *
 * <p><b>级别策略</b>：常规出入日志打 <b>INFO</b>——沿用既有任务约定（前版 AccessLogFilter
 * 即每请求一条 INFO 单行审计日志，为 INFO 高频的既定例外），日志量以「每请求恰好一行 +
 * 内容最小化（不打 body/headers/token）」控制；慢请求（耗时 ≥
 * {@code smartwash.request-log.slow-threshold-ms}，默认 2000ms）仍为 INFO 并追加
 * (慢请求) 标记，耗时字段恒在行内，便于后续调整阈值。绝不打印请求体/响应体
 * （响应含手机号、余额），query 串经 {@link LogMasker#maskQuery} 脱敏后才进日志
 * （password/token/code 等参数值掩码为 ***）。
 *
 * <p><b>排除路径</b>：actuator/swagger/静态资源等基础设施路径不产生出入日志，
 * 前缀清单由 {@code smartwash.request-log.exclude-paths} 配置，纯前缀匹配——
 * 配 {@code /actuator} 即覆盖其全部子路径。
 *
 * <p><b>注册顺序</b>见 {@link com.smartwash.config.LoggingFilterConfig}：order 必须大于
 * springSecurityFilterChain 的默认 order（-100），即位于安全过滤链之内层——这样 finally
 * 打印时 SecurityContext 尚未被 SecurityContextHolderFilter 清理，可取到登录用户内部 id。
 *
 * <p>userId 取 LoginUser 的内部 id（数据库主键，不含手机号）；未认证或主体非 LoginUser
 * （匿名/字符串 principal）记 anonymous。JWT sub 形如 user-{手机号}，本过滤器永不打印 sub。
 *
 * <p>已知覆盖范围：401 短路请求（JWT 缺失/无效/黑名单）由 JwtAuthenticationFilter 直接写响应、
 * 不再调用后续过滤器，因此不产生本访问日志行，由其内部 warn 留痕；SSE 等异步响应在初始
 * 线程返回时打印，status 为初始响应状态。
 */
@Slf4j
@Component
public class AccessLogFilter extends OncePerRequestFilter {

    private final RequestLogProperties properties;
    private final LogMasker logMasker;

    public AccessLogFilter(RequestLogProperties properties) {
        this.properties = properties;
        // 默认清单 + 配置追加字段，构造期合并一次，请求期零开销
        this.logMasker = new LogMasker(properties.getMaskFields());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 开关关闭或命中排除前缀（actuator/swagger/静态资源）时不做出入日志
        if (!properties.isAccessLogEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        List<String> excludePaths = properties.getExcludePaths();
        return excludePaths != null && excludePaths.stream().anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 保证异常路径也打印且只打印一行；耗时与状态在链路返回后读取
            long costMs = System.currentTimeMillis() - start;
            String message = buildLogMessage(request, response, costMs);
            // 常规与慢请求均为 INFO（既有任务约定）；慢请求追加 (慢请求) 标记便于检索
            log.info(message);
        }
    }

    /**
     * 拼装单行日志内容：method path?query status 耗时 userId。
     * query 串先脱敏（敏感参数值掩码），只打摘要、不打请求体。
     */
    private String buildLogMessage(HttpServletRequest request, HttpServletResponse response, long costMs) {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            path = path + "?" + logMasker.maskQuery(queryString);
        }
        boolean slow = costMs >= properties.getSlowThresholdMs();
        return "访问请求" + (slow ? "(慢请求)" : "") + ": " + request.getMethod()
                + " " + path
                + " status=" + response.getStatus()
                + " 耗时=" + costMs + "ms"
                + " userId=" + resolveUserId();
    }

    /**
     * 取登录用户内部 id；未认证或主体非 LoginUser 时记 anonymous。
     * 只取 LoginUser.userId（内部主键），不取 username/sub，避免手机号入日志。
     */
    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return String.valueOf(loginUser.getUserId());
        }
        return "anonymous";
    }
}
