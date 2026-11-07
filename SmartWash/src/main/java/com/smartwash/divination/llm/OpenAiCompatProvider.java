package com.smartwash.divination.llm;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OpenAI 兼容供应商配置（运行时）。
 * 一个实例 = 一个供应商配置（来自 DB div_model_config 或 yml bootstrap）。
 */
@Data
@AllArgsConstructor
public class OpenAiCompatProvider {

    /** 显示名（落库 div_interpretation.provider / 日志打印用） */
    private String name;
    private String baseUrl;
    private String apiKey;
    private String model;
    private int priority;
    private boolean enabled;

    /** 配置来源：platform=平台目录, user=用户 BYOK, bootstrap=yml 兜底 */
    private String source;

    /** 配置 ID（平台目录或用户 BYOK 的 id） */
    private Long configId;

    /**
     * 熔断窗口键（与显示名分离）。
     *
     * <p>BYOK 显示名取用户自填模型名（多为 custom_model），不同用户极易同名——
     * 若直接用 name 聚合熔断窗口，会跨用户互相污染，且与平台目录同名模型跨层互撞。
     * 故熔断键按配置实例唯一构造：user:{configId} / platform:{configId} / bootstrap:{name}。</p>
     */
    private String circuitKey;
}
