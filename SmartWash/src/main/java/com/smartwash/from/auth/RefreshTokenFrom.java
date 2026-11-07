package com.smartwash.from.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求对象：携带登录/注册/上次刷新下发的 refreshToken，换取全新的双 token
 */
@Data
public class RefreshTokenFrom {

    /** 刷新令牌（typ=refresh，7 天有效期，一次性使用） */
    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;
}
