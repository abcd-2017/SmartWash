package com.smartwash.divination.from;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 管理端保存/更新模型目录条目请求 DTO（POST /admin/div/models）。
 *
 * <p>API Key 只进不出：明文仅经 apiKey 字段单向传入加密入库，任何查询接口都不回传明文/密文；
 * 更新时 apiKey 留空表示不更换密钥。</p>
 */
@Data
public class SaveModelFrom {

    /** 编辑时传 id，新增为空 */
    private Long id;

    @NotBlank(message = "厂商不能为空")
    private String vendor;

    @NotBlank(message = "显示名不能为空")
    private String name;

    /** 接入协议，缺省 openai_compat */
    private String provider;

    @NotBlank(message = "接入点不能为空")
    @Pattern(regexp = "^https?://.+", message = "接入点必须为 http(s) 地址")
    private String baseUrl;

    @NotBlank(message = "模型标识不能为空")
    private String modelId;

    /** 明文 API Key（目录模式恒空串占位；管理端演示接入时可录入） */
    private String apiKey;

    /** 客户端列表排序，越小越靠前 */
    private Integer priority;

    private Integer enabled;
}
