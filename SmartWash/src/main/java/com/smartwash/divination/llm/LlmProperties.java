package com.smartwash.divination.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 观象台 LLM 配置绑定（div.llm.*）。
 * bootstrap 兜底：仅当 DB 平台配置(div_model_config)缺失/事故时启用；
 * 正式配置由管理端在 Web 后台维护（存 div_model_config，密钥加密入库）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "div.llm")
public class LlmProperties {

    /** 供应商列表（bootstrap 兜底） */
    private List<ProviderConfig> providers = new ArrayList<>();

    @Data
    public static class ProviderConfig {
        private String name;
        private String baseUrl;
        /** 环境变量注入，禁止硬编码 */
        private String apiKey;
        private String model;
        /** 优先级（越小越优先） */
        private int priority = 100;
        private boolean enabled = true;
    }
}
