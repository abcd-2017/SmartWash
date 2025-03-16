package com.smartwash.common;

import lombok.Getter;

@Getter
public class DefaultConstant {
    public static final String DEFAULT_PASSWORD = "123456";

    public static final String ADMIN_DEFAULT_PASSWORD = "admin123456";

    public static final String ADMIN_USER_LOGIN_TYPE = "admin";
    public static final String USER_LOGIN_TYPE = "user";

    public static final String Captcha_Code = "captcha";
    public static final Long Captcha_Timeout = 1000 * 60 * 10L;
}
