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
    @DisplayName("访问令牌 typ=access，有效期约 2 小时")
    void generatorAccessToken_typAndTtl() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generatorAccessToken("test-user");
        Claims claims = jwtUtil.getPayloadFromToken(token);

        assertEquals(JwtUtil.TOKEN_TYPE_ACCESS, jwtUtil.getTokenType(claims), "访问令牌必须携带 typ=access");
        long ttlMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(JwtUtil.ACCESS_TOKEN_TTL, ttlMillis, 2000L, "访问令牌有效期应为 2 小时（容差 2s）");
        assertTrue(claims.getExpiration().getTime() > before, "有效期必须在签发时刻之后");
    }

    @Test
    @DisplayName("刷新令牌 typ=refresh，有效期约 7 天")
    void generatorRefreshToken_typAndTtl() {
        String token = jwtUtil.generatorRefreshToken("user-13800138000");
        Claims claims = jwtUtil.getPayloadFromToken(token);

        assertEquals(JwtUtil.TOKEN_TYPE_REFRESH, jwtUtil.getTokenType(claims), "刷新令牌必须携带 typ=refresh");
        assertEquals("user-13800138000", claims.getSubject(), "刷新令牌 sub 必须与访问令牌同构");
        long ttlMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(JwtUtil.REFRESH_TOKEN_TTL, ttlMillis, 2000L, "刷新令牌有效期应为 7 天（容差 2s）");
    }

    @Test
    @DisplayName("双 token 的 jti 互不相同（可分别拉黑）")
    void tokenPair_distinctJti() {
        String access = jwtUtil.generatorAccessToken("test-user");
        String refresh = jwtUtil.generatorRefreshToken("test-user");

        Claims accessClaims = jwtUtil.getPayloadFromToken(access);
        Claims refreshClaims = jwtUtil.getPayloadFromToken(refresh);

        assertNotEquals(accessClaims.getId(), refreshClaims.getId(), "access 与 refresh 的 jti 必须不同");
        assertEquals(accessClaims.getSubject(), refreshClaims.getSubject(), "双 token 的 sub 必须一致");
    }

    @Test
    @DisplayName("剩余有效期计算：未过期返回正数，过期返回 0")
    void getRemainingValiditySeconds() {
        String token = jwtUtil.generatorAccessToken("test-user");
        Claims claims = jwtUtil.getPayloadFromToken(token);
        assertTrue(jwtUtil.getRemainingValiditySeconds(claims) > 0, "未过期 token 剩余有效期必须为正数");
        assertTrue(jwtUtil.getRemainingValiditySeconds(claims) <= JwtUtil.ACCESS_TOKEN_TTL / 1000, "剩余有效期不得超过 TTL");
    }
}
