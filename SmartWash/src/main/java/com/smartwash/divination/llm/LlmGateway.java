package com.smartwash.divination.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 网关：熔断 + 计量（路由已零兜底化）。
 *
 * <p>平台不给兜底：解读只消费用户 BYOK 配置（{@code div_user_api_config}），
 * 由 Service 层查库解密构造后传入 {@link #resolveProvider}。用户未配置
 * （null / disabled）抛 {@link NoProviderConfiguredException}，解读链路转 SSE 428
 * 引导客户端跳设置页；无平台默认模型、无 yml bootstrap 兜底。</p>
 *
 * <p>熔断：按供应商滑动窗口错误率熔断（简易实现，不引 resilience4j）。
 * 熔断键用 {@link OpenAiCompatProvider#getCircuitKey()}（与显示名分离）：
 * BYOK 按用户 configId 隔离，不同用户同名模型互不污染。</p>
 */
@Slf4j
@Component
public class LlmGateway {

    /** 熔断计数窗口：provider → {errors, total} */
    private final ConcurrentHashMap<String, ErrorWindow> circuitWindows = new ConcurrentHashMap<>();

    /** 熔断阈值：错误率 > 50% 且请求数 >= 10 时熔断（MIN_REQUESTS 包可见供单测引用） */
    private static final double CIRCUIT_BREAK_THRESHOLD = 0.5;
    static final int CIRCUIT_BREAK_MIN_REQUESTS = 10;
    /** 熔断冷却时间（ms） */
    private static final long CIRCUIT_BREAK_COOLDOWN_MS = 60_000;

    /**
     * 解析当前请求应使用的供应商（仅用户 BYOK，零兜底）。
     *
     * @param userConfig 用户 BYOK 供应商（Service 层查库解密构造；未配置/无效为 null）
     * @return 解析结果（含供应商 + 来源标记 + 配置 ID）
     * @throws NoProviderConfiguredException 用户未配置或配置无效（解读链路转 428）
     * @throws IllegalStateException         配置存在但供应商熔断中（解读链路转 503）
     */
    public ResolvedProvider resolveProvider(OpenAiCompatProvider userConfig) {
        if (userConfig == null || !userConfig.isEnabled()) {
            throw new NoProviderConfiguredException();
        }
        if (isCircuitBroken(circuitKeyOf(userConfig))) {
            log.warn("用户模型供应商熔断中, provider: {}", userConfig.getName());
            throw new IllegalStateException("用户模型供应商熔断中");
        }
        return new ResolvedProvider(userConfig, "user", userConfig.getConfigId());
    }

    /** 记录供应商调用结果（用于熔断判断）：按 provider 的熔断键聚合（与显示名无关） */
    public void recordResult(OpenAiCompatProvider provider, boolean success) {
        recordResult(circuitKeyOf(provider), success);
    }

    /** 记录指定熔断键的调用结果（用于熔断判断） */
    public void recordResult(String circuitKey, boolean success) {
        ErrorWindow window = circuitWindows.computeIfAbsent(circuitKey, k -> new ErrorWindow());
        window.total.incrementAndGet();
        if (!success) {
            window.errors.incrementAndGet();
        }
    }

    /**
     * 取熔断键：优先 provider.circuitKey（按配置实例唯一，用户间隔离），
     * 未显式设置时回退显示名（兼容手工构造的 provider，行为与旧版一致）。
     */
    private String circuitKeyOf(OpenAiCompatProvider provider) {
        return provider.getCircuitKey() != null ? provider.getCircuitKey() : provider.getName();
    }

    /** 判断供应商是否熔断中 */
    public boolean isCircuitBroken(String providerName) {
        ErrorWindow window = circuitWindows.get(providerName);
        if (window == null) return false;
        long total = window.total.get();
        long errors = window.errors.get();
        if (total < CIRCUIT_BREAK_MIN_REQUESTS) return false;
        double errorRate = (double) errors / total;
        if (errorRate > CIRCUIT_BREAK_THRESHOLD) {
            // 首次触发熔断：标记冷却起点（原实现 lastBreakTime 初始为 0 导致 elapsed 恒超冷却期、
            // 直接 reset 清窗口——熔断从未真正生效，坏配置会被反复试呼）
            if (window.lastBreakTime == 0) {
                window.lastBreakTime = System.currentTimeMillis();
                log.warn("供应商错误率 {} 超阈值，熔断 {}ms: {}", errorRate, CIRCUIT_BREAK_COOLDOWN_MS, providerName);
                return true;
            }
            // 冷却期内
            long elapsed = System.currentTimeMillis() - window.lastBreakTime;
            if (elapsed < CIRCUIT_BREAK_COOLDOWN_MS) {
                return true;
            }
            // 冷却期过，重置窗口（半开放行探测）
            window.reset();
        }
        return false;
    }

    /**
     * 解析结果：供应商 + 来源标记 + 配置 ID。
     */
    @Data
    public static class ResolvedProvider {
        private final OpenAiCompatProvider provider;
        private final String keySource;
        private final Long configId;
    }

    /**
     * 错误窗口（简易滑动窗口）。
     */
    private static class ErrorWindow {
        final AtomicLong errors = new AtomicLong();
        final AtomicLong total = new AtomicLong();
        long lastBreakTime;

        void reset() {
            errors.set(0);
            total.set(0);
            lastBreakTime = System.currentTimeMillis();
        }
    }
}
