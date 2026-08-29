package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 可选模型目录项（GET /models 返回元素）。
 */
@Data
public class ModelVo {

    private Long id;

    private String name;

    private String provider;

    private String modelId;

    private Integer priority;

    /** 当前用户是否正在使用该模型（BYOK 或平台默认命中） */
    private Boolean active;
}
