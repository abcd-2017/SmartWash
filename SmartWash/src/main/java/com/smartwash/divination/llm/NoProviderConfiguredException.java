package com.smartwash.divination.llm;

/**
 * 无可用模型配置异常（平台零兜底语义）：用户未配置 BYOK / 配置被停用或删除。
 * 解读链路捕获后以 SSE error 428 下发，客户端据此路由拦截跳设置页（区别于 503 服务故障）。
 */
public class NoProviderConfiguredException extends RuntimeException {

    public NoProviderConfiguredException() {
        super("用户未配置解读模型");
    }
}
