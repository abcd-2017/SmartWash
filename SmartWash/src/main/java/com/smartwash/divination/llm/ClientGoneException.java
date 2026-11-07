package com.smartwash.divination.llm;

/**
 * 客户端断连异常（SseEmitter 已完成/已提交后仍尝试推送时触发）。
 *
 * 语义：客户端主动断开 SSE 连接，供应商链路本身没有失败——
 * 与 {@link LlmCallException}（供应商 HTTP/网络错误）严格区分：
 *   - 网关 {@link OpenAiCompatClient#chatWithFallback} 原样穿透（不回退、不包装）；
 *   - 调用方不记熔断失败、不回写错误帧，静默收尾。
 */
public class ClientGoneException extends RuntimeException {

    public ClientGoneException(String message) {
        super(message);
    }

    public ClientGoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
