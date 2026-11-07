package com.smartwash.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwash.service.JwtBlacklistService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.JsonUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 自定义登出处理器 —— 将 JWT 的 jti 加入黑名单使其失效。
 * 双 token 制下支持双拉黑：Authorization 头的 access 令牌必拉黑；
 * 请求体可选携带 {"refreshToken": "..."}，携带时将 refresh 令牌一并拉黑
 * （不携带时仅拉黑 access，行为向后兼容）。请求体中任何非法内容只记日志、不影响登出主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private final JwtBlacklistService jwtBlacklistService;
    private final JwtUtil jwtUtil;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // 拉黑 Authorization 头中的 access 令牌（原有行为，保持不变）
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                blacklistToken(jwtUtil.getPayloadFromToken(token), "access");
            } catch (Exception e) {
                log.warn("登出时解析 JWT 失败: {}", e.getMessage());
            }
        }
        // 拉黑请求体中可选携带的 refreshToken（双 token 制新增，向后兼容：不携带即跳过）
        blacklistRefreshTokenFromBody(request);
    }

    /**
     * 将负载对应的令牌加入黑名单，TTL 对齐剩余有效期
     *
     * @param claims JWT 负载
     * @param typ    令牌分型（仅用于日志）
     */
    private void blacklistToken(Claims claims, String typ) {
        String jti = claims.get("jti", String.class);
        long ttlSeconds = jwtUtil.getRemainingValiditySeconds(claims);
        if (jti != null && ttlSeconds > 0) {
            jwtBlacklistService.blacklist(jti, ttlSeconds);
            log.info("用户登出，{} 令牌已加入黑名单, jti: {}", typ, jti);
        }
    }

    /**
     * 解析登出请求体并拉黑可选的 refreshToken：
     * 请求体由登出处理器独占读取（无 controller 消费 /auth/logout 请求体），解析失败一律静默跳过。
     */
    private void blacklistRefreshTokenFromBody(HttpServletRequest request) {
        try {
            String body = request.getReader().lines().collect(java.util.stream.Collectors.joining());
            if (!StringUtils.hasText(body)) {
                return;
            }
            JsonNode refreshTokenNode = JsonUtils.readTree(body).get("refreshToken");
            String refreshToken = refreshTokenNode == null || refreshTokenNode.isNull()
                    ? null : refreshTokenNode.asText();
            if (!StringUtils.hasText(refreshToken)) {
                return;
            }
            blacklistToken(jwtUtil.getPayloadFromToken(refreshToken), "refresh");
        } catch (IOException e) {
            // 请求体读取失败不影响登出主流程（access 已拉黑）
            log.warn("登出请求体读取失败，跳过 refreshToken 拉黑: {}", e.getMessage());
        } catch (Exception e) {
            // 请求体非法或 refreshToken 无效（格式/签名/过期）：仅记日志，不影响登出主流程
            log.warn("登出请求体解析或 refreshToken 校验失败，跳过拉黑: {}", e.getMessage());
        }
    }
}
