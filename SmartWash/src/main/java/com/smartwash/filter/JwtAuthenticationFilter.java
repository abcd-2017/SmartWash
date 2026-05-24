package com.smartwash.filter;

import com.smartwash.exception.UserAuthenticationException;
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

        if (!requestURI.startsWith("/admin/") && !requestURI.startsWith("/web/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // 通过 getTokenFromRequest(request) 方法，尝试从 HTTP 请求的 Authorization 头中提取 JWT。如果 token 存在且以 Bearer 开头，则提取出 token 部分。
        String token = getTokenFromRequest(request);

        /* 校验 token
        使用 jwtTokenProvider.validateToken(token) 校验 JWT 的合法性。
        例如，检查 token 是否过期、是否篡改等。如果 token 无效或过期，认证过程会被跳过，后续请求会被拒绝。
        */
        if (StringUtils.hasText(token)) {
            try {
                // 检查 token 是否在黑名单中（已登出）
                Claims claims = jwtUtil.getPayloadFromToken(token);
                String jti = claims.get("jti", String.class);
                if (jti != null && jwtBlacklistService.isBlacklisted(jti)) {
                    log.warn("JWT Token 已在黑名单中, jti: {}", jti);
                    SecurityContextHolder.clearContext();
                    UserContextHolder.clear();
                    throw new UserAuthenticationException("登录失效，请重新登录");
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
                log.warn("JWT Token 验证失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                UserContextHolder.clear();
                throw new UserAuthenticationException("登录失效，请重新登录");
            }
        } else {
            log.warn("JWT Token 缺失或无效");
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            throw new UserAuthenticationException("登录失效，请重新登录");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
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
