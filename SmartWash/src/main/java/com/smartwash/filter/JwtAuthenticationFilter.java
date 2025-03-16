package com.smartwash.filter;

import com.smartwash.exception.UserAuthenticationException;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.LoginUser;
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

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }


    /*这是过滤器的核心方法，它会在每次 HTTP 请求时被调用。方法中的处理流程如下：*/
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        if (requestURI.startsWith("/auth/")) {
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
                // 从 token 获取 username
                String username = jwtUtil.getUserNameFromToken(token);

                // 加载与 token 关联的用户
                LoginUser userDetails = (LoginUser) userDetailsService.loadUserByUsername(username);

                /*
                 * 创建一个 UsernamePasswordAuthenticationToken 实例，传入 userDetails 和该用户的权限（userDetails.getAuthorities()）。
                 * UsernamePasswordAuthenticationToken 是 Spring Security 用来封装用户身份信息的一个对象，它包含了用户的身份信息和权限。
                 * */
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                /*
                 * 使用 SecurityContextHolder.getContext().setAuthentication(authenticationToken) 将身份认证信息（即 authenticationToken）存储在 Spring Security 的上下文中。
                 * 这样，Spring Security 在处理后续请求时，就能基于这个认证信息对请求进行授权控制。
                 * */
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } catch (Exception e) {
                // 记录日志并清除认证上下文
                log.warn("JWT Token validation failed: " + e.getMessage());
                SecurityContextHolder.clearContext();
                throw new UserAuthenticationException("登录失效，请重新登录");
            }
        } else {
            // 无 token 或 token 无效，清除认证上下文
            logger.warn("JWT Token is missing or invalid");
            SecurityContextHolder.clearContext();
            throw new UserAuthenticationException("登录失效，请重新登录");
        }

        /*最后，调用 filterChain.doFilter(request, response) 让请求继续传递给下一个过滤器或最终的目标（如 Controller）。
        这一步是确保请求能够正常进入应用的下一阶段*/
        filterChain.doFilter(request, response);
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
