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
}
