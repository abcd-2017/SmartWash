package com.smartwash.exception;

import lombok.Getter;

/**
 * 业务异常：全局由 ExceptionControllerAdvice 转为 {code,message,data} 信封。
 * code 为空时沿用默认失败码（201）；需要表达 404/403/409/413 等 HTTP 语义时
 * 使用带 code 的构造器（如设备投递的「过期一律 404」「占用 409」「超限 413」）。
 */
@Getter
public class CustomExceptions extends RuntimeException {

    /** 业务码：null 时由 advice 填充默认失败码 */
    private final Integer code;

    public CustomExceptions(String message) {
        super(message);
        this.code = null;
    }

    public CustomExceptions(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
