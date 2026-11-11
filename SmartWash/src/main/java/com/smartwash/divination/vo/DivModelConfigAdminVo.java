package com.smartwash.divination.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台模型目录管理端视图（GET /admin/div/models 返回元素）。
 * 不含 api_key_cipher 密文与 key_version——密文永不出接口，管理端仅见掩码。
 */
@Data
public class DivModelConfigAdminVo {

    private Long id;

    /** 厂商名（客户端两级选择的一级分组） */
    private String vendor;

    /** 模型显示名 */
    private String name;

    /** 接入协议（当前仅 openai_compat） */
    private String provider;

    private String baseUrl;

    /** 供应商模型标识 */
    private String modelId;

    /** 掩码 sk-****abc4（目录模式密钥由用户自带，恒为空） */
    private String apiKeyMask;

    /** 客户端列表排序，越小越靠前 */
    private Integer priority;

    private Integer enabled;

    private LocalDateTime lastTestAt;

    private Integer lastTestOk;

    private LocalDateTime updatedAt;
}
