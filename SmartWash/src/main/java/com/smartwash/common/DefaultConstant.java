package com.smartwash.common;

import lombok.Getter;

import java.security.SecureRandom;

@Getter
public class DefaultConstant {

    public static final String ADMIN_USER_LOGIN_TYPE = "admin";
    public static final String USER_LOGIN_TYPE = "user";

    public static final String Captcha_Code = "captcha";
    public static final Long Captcha_Timeout = 1000 * 60 * 10L;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成随机初始密码（12位，包含大小写字母、数字和特殊字符）
     */
    public static String generateDefaultPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
