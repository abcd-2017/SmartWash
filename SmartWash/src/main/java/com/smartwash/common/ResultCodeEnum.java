package com.smartwash.common;

import lombok.Getter;

@Getter
public enum ResultCodeEnum {

    SUCCESS(200, "成功"),
    FAIL(201, "失败"), UNAUTHORIZED(401, "登录失效，请重新登录"),
    SERVICE_ERROR(2012, "服务异常");

    private final Integer code;

    private final String message;

    private ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}