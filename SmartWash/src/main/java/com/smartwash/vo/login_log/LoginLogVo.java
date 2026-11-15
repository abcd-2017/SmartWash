package com.smartwash.vo.login_log;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志视图对象（管理端列表页消费）。
 */
@Data
public class LoginLogVo {

    private Long id;

    private Long userId;

    /** 登录账号（用户端手机号 / 管理端用户名） */
    private String phone;

    private String platform;

    private String appVersion;

    private String ua;

    private String networkType;

    private String ip;

    /** 登录结果：success/fail */
    private String result;

    /** 结果文案：成功/失败 */
    private String resultDesc;

    private LocalDateTime createdAt;
}
