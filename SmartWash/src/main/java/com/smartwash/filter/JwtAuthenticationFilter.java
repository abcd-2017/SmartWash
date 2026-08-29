package com.smartwash.filter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import com.smartwash.service.JwtBlacklistService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * 注意：本过滤器位于 ExceptionTranslationFilter 之前，认证失败抛出的 RuntimeException
 * 无法被 @RestControllerAdvice / AuthenticationEntryPoint 捕获（最终变成 500），
 * 因此认证失败时在过滤器内直接写响应：HTTP 401 + Result 统一信封 {code, message, data}，
 * 不再向下游抛异常；公开路径与 token 正常的链路行为保持不变。
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final WebAuthenticationDetailsSource AUTHENTICATION_DETAILS_SOURCE = new WebAuthenticationDetailsSource();

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final JwtBlacklistService jwtBlacklistService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService, JwtBlacklistService jwtBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.jwtBlacklistService = jwtBlacklistService;
    }


    /*这是过滤器的核心方法，它会在每次 HTTP 请求时被调用。方法中的处理流程如下：*/
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // 非认证路径（/auth/**、/web/** 公开接口等）直接放行，行为保持不变
        if (!requestURI.startsWith("/admin/") && !requestURI.startsWith("/web/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 通过 getTokenFromRequest(request) 方法，尝试从 HTTP 请求的 Authorization 头中提取 JWT。如果 token 存在且以 Bearer 开头，则提取出 token 部分。
        String token = getTokenFromRequest(request);

        /* 校验 token
        使用 jwtUtil.getPayloadFromToken(token) 校验 JWT 的合法性。
        例如，检查 token 是否过期、是否篡改等。token 缺失或校验失败时，
        直接写 401 统一信封响应并结束请求，不再向下游抛异常。
        */
        if (!StringUtils.hasText(token)) {
            log.warn("JWT Token 缺失或无效, uri: {}", requestURI);
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            writeUnauthorized(response);
            return;
        }

        try {
            // 检查 token 是否在黑名单中（已登出）
            Claims claims = jwtUtil.getPayloadFromToken(token);
            String jti = claims.get("jti", String.class);
            if (jti != null && jwtBlacklistService.isBlacklisted(jti)) {
                log.warn("JWT Token 已在黑名单中, jti: {}", jti);
                SecurityContextHolder.clearContext();
                UserContextHolder.clear();
                writeUnauthorized(response);
                return;
            }

            // 从 token 获取 username
            String username = claims.get("sub", String.class);

            // 加载与 token 关联的用户
            LoginUser userDetails = (LoginUser) userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            authenticationToken.setDetails(AUTHENTICATION_DETAILS_SOURCE.buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            UserContextHolder.setUser(userDetails);
        } catch (Exception e) {
            // token 校验/用户加载失败：直接返回 401 统一信封，不再向下游抛 RuntimeException
            log.warn("JWT Token 验证失败, uri: {}, 原因: {}", requestURI, e.getMessage());
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            writeUnauthorized(response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }


    /**
     * 认证失败时直接写响应：HTTP 401 + application/json;charset=UTF-8，
     * 响应体为项目统一 Result 信封 {code, message, data}（FastJSON 2 序列化，
     * WriteMapNullValue 保留 data:null 与全局异常处理器输出结构一致）。
     * 写响应失败（如客户端已断开）仅记录日志，不再向上抛出。
     */
    private void writeUnauthorized(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(Result.build(null, ResultCodeEnum.UNAUTHORIZED), JSONWriter.Feature.WriteMapNullValue));
        } catch (IOException e) {
            log.warn("认证失败响应写出失败（客户端可能已断开连接）: {}", e.getMessage());
        }
    }


    // 从请求头获取 JWT 格式为:Authorization: Bearer <token>
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 去掉 "Bearer " 前缀
        }
        return null;
    }
}
