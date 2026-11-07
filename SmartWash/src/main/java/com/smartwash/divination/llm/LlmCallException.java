package com.smartwash.divination.llm;

import lombok.Getter;

/**
 * LLM 调用异常（供应商 HTTP 错误 / 网络异常 / 响应解析失败）。
 *
 * message 字段只承载脱敏后的信息，绝不包含 apiKey。
 */
@Getter
public class LlmCallException extends RuntimeException {

    /** 供应商 HTTP 状态码；0 = 网络异常/超时/非预期响应（无法归类到具体状态码） */
    private final int httpStatus;

    /** 供应商错误信息（已脱敏，不含 apiKey） */
    private final String providerMessage;

    public LlmCallException(int httpStatus, String providerMessage) {
        super("LLM 调用失败, httpStatus: " + httpStatus + ", message: " + providerMessage);
        this.httpStatus = httpStatus;
        this.providerMessage = providerMessage;
    }

    /** 网络类异常（连接失败/超时/响应无法解析） */
    public static LlmCallException network(String message) {
        return new LlmCallException(0, message);
    }

    /** 是否为 4xx 客户端类错误（密钥无效/参数错误/配额限制）——此类错误重试无意义，不做非流式回退 */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }
}
