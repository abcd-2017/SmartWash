package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 模型目录项（树形结构中挂在 {@link VendorGroupVo} 下的二级元素）。
 * 目录只提供模型标识，密钥始终由用户自带（BYOK），目录不存任何密钥。
 */
@Data
public class ModelVo {

    /** div_model_config.id——用户保存配置时回传此 id（目录模式） */
    private Long id;

    /** 模型显示名 */
    private String name;

    /** 供应商模型标识（发给 LLM API 的 model 字段） */
    private String modelId;

    /** 模型排序权重（升序） */
    private Integer priority;

    /** 当前用户是否正在使用该模型 */
    private Boolean active;
}
