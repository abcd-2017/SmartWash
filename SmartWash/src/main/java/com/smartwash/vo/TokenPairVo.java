package com.smartwash.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新令牌响应视图对象：/auth/refresh 一次性轮换后签发的新双 token。
 * 不返回 role——角色语义以登录响应为准，刷新不重查角色（零 DB 依赖）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenPairVo {

    /** 新访问令牌（typ=access，2 小时有效期），后续请求以 Bearer Token 携带 */
    private String token;

    /** 新刷新令牌（typ=refresh，7 天有效期），一次性使用：每次刷新后旧 refreshToken 立即失效 */
    private String refreshToken;
}
