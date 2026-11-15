package com.smartwash.common.client;

import org.slf4j.MDC;

/**
 * 客户端平台上下文持有者：ThreadLocal 供业务代码读取，MDC 供日志输出。
 *
 * <p>由 {@link ClientContextFilter} 在请求进入时写入、请求结束（含异常）时清理。
 * ThreadLocal 保证同线程业务代码可直接 {@link #get()}；MDC 键（{@code client_platform} 等）
 * 供 logback pattern 引用，做到「请求级平台信息随日志行输出」。
 *
 * <p>清理必须放在过滤器 finally，防止容器线程复用导致上下文串扰。
 */
public final class ClientContextHolder {

    /** MDC key：平台标识（logback pattern 可用 %X{client_platform} 引用） */
    public static final String MDC_PLATFORM = "client_platform";
    /** MDC key：App 版本号 */
    public static final String MDC_APP_VERSION = "client_app_version";
    /** MDC key：网络类型 */
    public static final String MDC_NETWORK_TYPE = "client_network_type";

    private static final ThreadLocal<ClientContext> HOLDER = new ThreadLocal<>();

    private ClientContextHolder() {
    }

    /**
     * 写入上下文并同步 MDC。context 为 null 时仅清空（防御非 Web 线程误用）。
     */
    public static void set(ClientContext context) {
        HOLDER.set(context);
        if (context == null) {
            removeMdc();
            return;
        }
        MDC.put(MDC_PLATFORM, context.getPlatform());
        // MDC 值不允许 null，统一以空串占位（仅用于日志展示，不参与业务）
        MDC.put(MDC_APP_VERSION, context.getAppVersion() == null ? "" : context.getAppVersion());
        MDC.put(MDC_NETWORK_TYPE, context.getNetworkType());
    }

    /**
     * 获取当前请求上下文；非 Web 线程或未设置时返回 null（调用方需自行兜底）。
     */
    public static ClientContext get() {
        return HOLDER.get();
    }

    /**
     * 清理 ThreadLocal 与 MDC，必须在请求结束时调用。
     */
    public static void clear() {
        HOLDER.remove();
        removeMdc();
    }

    private static void removeMdc() {
        MDC.remove(MDC_PLATFORM);
        MDC.remove(MDC_APP_VERSION);
        MDC.remove(MDC_NETWORK_TYPE);
    }
}
