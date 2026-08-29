package com.smartwash.divination.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 网关：路由（主备+熔断）+ 重试 + token 计量。
 *
 * 三层密钥解析（每请求）：
 *   1. 用户 BYOK（div_user_api_config，enabled 且 verified）
 *   2. 平台默认模型（div_platform_setting.default_model_id → div_model_config）
 *   3. yml bootstrap 兜底
 *
 * 熔断：按供应商滑动窗口错误率熔断（简易实现，不引 resilience4j）。
 */
@Slf4j
@Component
public class LlmGateway {

    private final LlmProperties llmProperties;

    /** 熔断计数窗口：provider → {errors, total} */
    private final ConcurrentHashMap<String, ErrorWindow> circuitWindows = new ConcurrentHashMap<>();

    /** 熔断阈值：错误率 > 50% 且请求数 >= 10 时熔断 */
    private static final double CIRCUIT_BREAK_THRESHOLD = 0.5;
    private static final int CIRCUIT_BREAK_MIN_REQUESTS = 10;
    /** 熔断冷却时间（ms） */
    private static final long CIRCUIT_BREAK_COOLDOWN_MS = 60_000;

    public LlmGateway(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * 解析当前请求应使用的供应商（三层密钥解析）。
     *
     * @param userConfig 用户 BYOK 配置（可为 null）
     * @return 解析结果（含供应商 + 来源标记）
     */
    public ResolvedProvider resolveProvider(OpenAiCompatProvider userConfig) {
        // 优先级 1：用户 BYOK
        if (userConfig != null && userConfig.isEnabled()) {
            if (!isCircuitBroken(userConfig.getName())) {
                return new ResolvedProvider(userConfig, "user", userConfig.getConfigId());
            }
            log.warn("用户 BYOK 供应商熔断中，降级到平台默认: {}", userConfig.getName());
        }

        // 优先级 2：平台默认模型（由调用方注入，此处 fallback 到 bootstrap）
        // 优先级 3：yml bootstrap 兜底
        List<OpenAiCompatProvider> bootstrapProviders = getBootstrapProviders();
        for (OpenAiCompatProvider provider : bootstrapProviders) {
            if (!isCircuitBroken(provider.getName())) {
                return new ResolvedProvider(provider, "platform", null);
            }
        }

        throw new IllegalStateException("暂无可用 LLM 供应商，所有供应商均熔断或未配置");
    }

    /**
     * 获取 bootstrap 供应商列表（按优先级排序）。
     */
    public List<OpenAiCompatProvider> getBootstrapProviders() {
        List<OpenAiCompatProvider> providers = new ArrayList<>();
        if (llmProperties.getProviders() != null) {
            for (LlmProperties.ProviderConfig cfg : llmProperties.getProviders()) {
                if (cfg.isEnabled() && StringUtils.hasText(cfg.getApiKey())) {
                    providers.add(new OpenAiCompatProvider(
                            cfg.getName(), cfg.getBaseUrl(), cfg.getApiKey(),
                            cfg.getModel(), cfg.getPriority(), true, "bootstrap", null
                    ));
                }
            }
        }
        providers.sort(Comparator.comparingInt(OpenAiCompatProvider::getPriority));
        return providers;
    }

    /** 记录供应商调用结果（用于熔断判断） */
    public void recordResult(String providerName, boolean success) {
        ErrorWindow window = circuitWindows.computeIfAbsent(providerName, k -> new ErrorWindow());
        window.total.incrementAndGet();
        if (!success) {
            window.errors.incrementAndGet();
        }
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
            // 检查是否已过冷却期
            long elapsed = System.currentTimeMillis() - window.lastBreakTime;
            if (elapsed < CIRCUIT_BREAK_COOLDOWN_MS) {
                return true;
            }
            // 冷却期过，重置窗口
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
