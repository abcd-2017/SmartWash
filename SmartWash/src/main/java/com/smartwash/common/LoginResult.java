package com.smartwash.common;

import lombok.Getter;

/**
 * 登录结果（login_log.result）。
 *
 * <p>仅记录成功/失败两态：锁定、密码错误、未注册、验证码错误等统一记为 {@link #FAIL}，
 * 具体原因由登录接口响应文案与既有日志承载，登录日志只做审计与运营统计。
 */
@Getter
public enum LoginResult {

    SUCCESS("success", "成功"),
    FAIL("fail", "失败");

    private final String status;
    private final String description;

    LoginResult(String status, String description) {
        this.status = status;
        this.description = description;
    }
}
