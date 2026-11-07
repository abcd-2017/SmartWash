package com.smartwash.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT工具类
 */
@Slf4j
@Component
public class JwtUtil {

    /** 令牌分型 claim 名：access=访问令牌（访问受保护端点），refresh=刷新令牌（仅用于 /auth/refresh 换发） */
    public static final String CLAIM_TYP = "typ";
    /** 令牌分型取值：访问令牌 */
    public static final String TOKEN_TYPE_ACCESS = "access";
    /** 令牌分型取值：刷新令牌 */
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    /** 访问令牌有效期：2 小时 */
    public static final Long ACCESS_TOKEN_TTL = 2 * 60 * 60 * 1000L;
    /** 刷新令牌有效期：7 天 */
    public static final Long REFRESH_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000L; // 7天
    private static final String ISSUER = "SmartWash";
    /** JWT 密钥解码后的最小字节数（HmacSHA256 建议 32 字节以上），不足则拒绝启动 */
    private static final int JWT_SECRET_MIN_BYTES = 32;

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // fail-fast：密钥未配置或强度不足时直接终止启动，禁止以空密钥/弱密钥对外签发 token
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 签名密钥未配置：请通过环境变量 JWT_SECRET 注入 Base64 编码的随机密钥（解码后至少 32 字节），应用拒绝以空密钥启动");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT 签名密钥格式非法：JWT_SECRET 必须为 Base64 编码字符串", e);
        }
        if (keyBytes.length < JWT_SECRET_MIN_BYTES) {
            throw new IllegalStateException(
                    "JWT 签名密钥强度不足：解码后为 " + keyBytes.length + " 字节，低于安全下限 32 字节，请更换更长的随机密钥后重启");
        }
        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 初始化负载内数据
     *
     * @param username  用户名
     * @param tokenType 令牌分型（access / refresh）
     * @param ttlMillis 有效期（毫秒）
     * @return 负载集合
     */
    private Map<String, Object> initClaims(String username, String tokenType, Long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        //"iss" (Issuer): 代表 JWT 的签发者。在这个字段中填入一个字符串，表示该 JWT 是由谁签发的。例如，可以填入你的应用程序的名称或标识符。
        claims.put("iss", ISSUER);
        //"sub" (Subject): 代表 JWT 的主题，即该 JWT 所面向的用户。可以是用户的唯一标识符或者其他相关信息。
        claims.put("sub", username);
        //"typ" (Type): 令牌分型，access=访问令牌 / refresh=刷新令牌；旧格式（无 typ）一律视为 access，兼容存量会话。
        claims.put(CLAIM_TYP, tokenType);
        //"exp" (Expiration Time): 代表 JWT 的过期时间。通常以 UNIX 时间戳表示，表示在这个时间之后该 JWT 将会过期。建议设定一个未来的时间点以保证 JWT 的有效性，比如一个小时、一天、一个月后的时间。
        claims.put("exp", generatorExpirationDate(ttlMillis));
        //"aud" (Audience): 代表 JWT 的接收者。这个字段可以填入该 JWT 预期的接收者，可以是单个用户、一组用户、或者某个服务。
//        claims.put("aud", "internal use");
        //"iat" (Issued At): 代表 JWT 的签发时间。同样使用 UNIX 时间戳表示。
        claims.put("iat", new Date());
        //"jti" (JWT ID): JWT 的唯一标识符。这个字段可以用来标识 JWT 的唯一性，避免重放攻击等问题。
        claims.put("jti", getUUID());
        //"nbf" (Not Before): 代表 JWT 的生效时间。在这个时间之前 JWT 不会生效，通常也是一个 UNIX 时间戳。我这里不填，没这个需求
        return claims;
    }

    /**
     * 生成访问令牌（typ=access，2 小时有效期），用于访问受保护端点
     *
     * @param username 用户名（admin-{用户名} / user-{手机号}）
     * @return 访问令牌
     */
    public String generatorAccessToken(String username) {
        return buildToken(username, TOKEN_TYPE_ACCESS, ACCESS_TOKEN_TTL);
    }

    /**
     * 生成刷新令牌（typ=refresh，7 天有效期），仅用于 /auth/refresh 换发新的双 token，一次性使用
     *
     * @param username 用户名（admin-{用户名} / user-{手机号}）
     * @return 刷新令牌
     */
    public String generatorRefreshToken(String username) {
        return buildToken(username, TOKEN_TYPE_REFRESH, REFRESH_TOKEN_TTL);
    }

    /**
     * 根据负载生成JWT token（兼容保留：等价于访问令牌）
     *
     * @return token
     */
    public String generatorToken(String username) {
        return generatorAccessToken(username);
    }

    /**
     * 按分型与有效期构建 JWT
     */
    private String buildToken(String username, String tokenType, Long ttlMillis) {
        log.debug("生成 JWT, subject: {}, typ: {}", username, tokenType);
        Map<String, Object> claims = initClaims(username, tokenType, ttlMillis);
        return Jwts.builder()
                   .claims(claims)
                   .signWith(key)
                   .compact();
    }


    /**
     * 从Token中获取负载中的Claims
     *
     * @param token token
     * @return 负载
     */
    public Claims getPayloadFromToken(String token) {
        //密钥实例
        return Jwts.parser()
                   .verifyWith(key)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
    }

    /**
     * 从Token中获取用户名
     *
     * @param token token
     * @return 用户名
     */
    public String getUserNameFromToken(String token) {
        String username;
        try {
            username = getPayloadFromToken(token).get("sub", String.class);
        } catch (Exception e) {
            log.warn("JWT 解析失败", e);
            username = null;
        }
        return username;
    }


    /**
     * 生成失效时间
     *
     * @param ttlMillis 有效期（毫秒）
     * @return 预计失效时间
     */
    private Date generatorExpirationDate(Long ttlMillis) {
        //预计失效时间为：token生成时间+预设期间
        return new Date(System.currentTimeMillis() + ttlMillis);
    }
    /**
     * 从负载中读取令牌分型
     *
     * @param claims JWT 负载
     * @return typ 取值（access / refresh）；旧格式（无 typ）返回 null，由调用方按 access 兼容处理
     */
    public String getTokenType(Claims claims) {
        return claims.get(CLAIM_TYP, String.class);
    }

    /**
     * 计算 token 剩余有效期（秒），供拉黑 jti 时对齐 TTL（到期自动从 Redis 清除）
     *
     * @param claims JWT 负载
     * @return 剩余秒数，已过期或无 exp 时返回 0
     */
    public long getRemainingValiditySeconds(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null) {
            return 0L;
        }
        return Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
    }
}
