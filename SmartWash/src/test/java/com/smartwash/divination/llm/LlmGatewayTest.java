package com.smartwash.divination.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LLM 网关（零兜底版）单测：只消费用户 BYOK 配置，无平台默认/无 yml bootstrap。
 */
class LlmGatewayTest {

    private final LlmGateway gateway = new LlmGateway();

    private OpenAiCompatProvider provider(String circuitKey, boolean enabled) {
        return new OpenAiCompatProvider("DeepSeek Flash", "https://api.deepseek.com",
                "sk-test", "deepseek-flash", 0, enabled, "user", 1L, circuitKey);
    }

    @Test
    @DisplayName("未配置（null）抛 NoProviderConfiguredException——解读链路转 SSE 428")
    void resolveNullThrowsNoProviderConfigured() {
        assertThatThrownBy(() -> gateway.resolveProvider(null))
                .isInstanceOf(NoProviderConfiguredException.class);
    }

    @Test
    @DisplayName("配置被停用（enabled=false）同 428 语义")
    void resolveDisabledThrowsNoProviderConfigured() {
        assertThatThrownBy(() -> gateway.resolveProvider(provider("u:1", false)))
                .isInstanceOf(NoProviderConfiguredException.class);
    }

    @Test
    @DisplayName("正常配置解析为 user 来源，configId 透传")
    void resolveOkUserSource() {
        LlmGateway.ResolvedProvider resolved = gateway.resolveProvider(provider("u:1", true));
        assertThat(resolved.getKeySource()).isEqualTo("user");
        assertThat(resolved.getConfigId()).isEqualTo(1L);
        assertThat(resolved.getProvider().getModel()).isEqualTo("deepseek-flash");
    }

    @Test
    @DisplayName("熔断键未设置时回退显示名（兼容手工构造）")
    void circuitKeyFallsBackToName() {
        OpenAiCompatProvider noKey = new OpenAiCompatProvider("同名模型", "https://api.deepseek.com",
                "sk-test", "deepseek-flash", 0, true, "user", 1L, null);
        assertThatCode(() -> gateway.resolveProvider(noKey)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("错误率超阈值且请求量达标 → 熔断抛 IllegalStateException（503 语义）")
    void circuitBreaksAfterThreshold() {
        OpenAiCompatProvider p = provider("u:break", true);
        for (int i = 0; i < LlmGateway.CIRCUIT_BREAK_MIN_REQUESTS; i++) {
            gateway.recordResult(p, false);
        }
        assertThatThrownBy(() -> gateway.resolveProvider(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("熔断");
    }

    @Test
    @DisplayName("请求量未达标不熔断")
    void noCircuitBelowMinRequests() {
        OpenAiCompatProvider p = provider("u:few", true);
        for (int i = 0; i < LlmGateway.CIRCUIT_BREAK_MIN_REQUESTS - 1; i++) {
            gateway.recordResult(p, false);
        }
        assertThatCode(() -> gateway.resolveProvider(p)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("熔断按 circuitKey 隔离：一个用户熔断不影响另一个用户")
    void circuitIsolatedByCircuitKey() {
        OpenAiCompatProvider broken = provider("u:broken", true);
        for (int i = 0; i < LlmGateway.CIRCUIT_BREAK_MIN_REQUESTS; i++) {
            gateway.recordResult(broken, false);
        }
        OpenAiCompatProvider other = provider("u:other", true);
        assertThatCode(() -> gateway.resolveProvider(other)).doesNotThrowAnyException();
        assertThatThrownBy(() -> gateway.resolveProvider(broken))
                .isInstanceOf(IllegalStateException.class);
    }
}
