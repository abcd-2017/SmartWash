package com.smartwash.config;

import com.smartwash.service.JwtBlacklistService;
import com.smartwash.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 自定义登出处理器 —— 将 JWT 的 jti 加入黑名单使其失效
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private final JwtBlacklistService jwtBlacklistService;
    private final JwtUtil jwtUtil;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            return;
        }
        String token = bearerToken.substring(7);
        try {
            Claims claims = jwtUtil.getPayloadFromToken(token);
            String jti = claims.get("jti", String.class);
            Date exp = claims.getExpiration();
            if (jti != null && exp != null) {
                long ttlSeconds = Math.max(0, (exp.getTime() - System.currentTimeMillis()) / 1000);
                jwtBlacklistService.blacklist(jti, ttlSeconds);
                log.info("用户登出，JWT 已加入黑名单, jti: {}", jti);
            }
        } catch (Exception e) {
            log.warn("登出时解析 JWT 失败: {}", e.getMessage());
        }
    }
}
