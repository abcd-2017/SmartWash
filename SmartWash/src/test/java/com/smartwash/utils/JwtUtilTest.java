package com.smartwash.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil 测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String TEST_SECRET = "dGVzdFNlY3JldEtleUZvckpXVFRlc3RpbmdQdXJwb3Nlcw==";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    @Test
    @DisplayName("生成 token 不为空")
    void generatorToken_notNull() {
        String token = jwtUtil.generatorToken("test-user");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("从 token 中获取用户名")
    void getUserNameFromToken() {
        String username = "user-13800138000";
        String token = jwtUtil.generatorToken(username);
        String extracted = jwtUtil.getUserNameFromToken(token);
        assertEquals(username, extracted);
    }

    @Test
    @DisplayName("token 包含正确的 claims")
    void tokenClaims() {
        String username = "admin-root";
        String token = jwtUtil.generatorToken(username);
        Claims claims = jwtUtil.getPayloadFromToken(token);

        assertEquals(username, claims.getSubject());
        assertEquals("SmartWash", claims.getIssuer());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
        assertNotNull(claims.getId());
    }

    @Test
    @DisplayName("无效 token 应返回 null")
    void getUserNameFromToken_invalid() {
        String result = jwtUtil.getUserNameFromToken("invalid.token.here");
        assertNull(result);
    }

    @Test
    @DisplayName("未过期 token 应可刷新")
    void canRefresh() {
        String token = jwtUtil.generatorToken("test-user");
        assertTrue(jwtUtil.canRefresh(token));
    }

    @Test
    @DisplayName("刷新 token 应生成新 token")
    void refreshToken() {
        String originalToken = jwtUtil.generatorToken("test-user");
        String newToken = jwtUtil.refreshToken(originalToken);

        assertNotNull(newToken);
        assertNotEquals(originalToken, newToken);
        assertEquals("test-user", jwtUtil.getUserNameFromToken(newToken));
    }
}
