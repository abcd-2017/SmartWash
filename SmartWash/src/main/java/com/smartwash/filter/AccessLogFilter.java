package com.smartwash.filter;

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

/**
 * 请求访问日志过滤器：请求完成时打一条 INFO 单行审计日志（任务约定的唯一 INFO 高频例外），
 * 内容仅含 method、uri、status、耗时、userId；绝不打印请求/响应体、token、密码、验证码。
 *
 * <p>注册顺序见 {@link com.smartwash.config.LoggingFilterConfig}：order 必须大于
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 保证异常路径也打印且只打印一行；耗时与状态在链路返回后读取
            long costMs = System.currentTimeMillis() - start;
            log.info("访问请求: {} {} status={} 耗时={}ms userId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), costMs, resolveUserId());
        }
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
