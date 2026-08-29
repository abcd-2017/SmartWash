package com.smartwash.divination.from;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存/测试用户 BYOK 请求 DTO。
 */
@Data
public class SaveUserApiFrom {

    @NotBlank(message = "接入点不能为空")
    private String baseUrl;

    @NotBlank(message = "模型不能为空")
    private String model;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;
}
