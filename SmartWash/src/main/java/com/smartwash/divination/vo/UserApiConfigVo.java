package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 用户 BYOK 配置视图（仅掩码，无明文/密文）。
 * apiKey 只写不读：查询只回掩码，更换只能覆盖更新。
 */
@Data
public class UserApiConfigVo {

    private Long id;

    /** 目录模式：平台模型目录条目 id（自定义模式为 null） */
    private Long modelConfigId;

    /** 目录模式：厂商名（如 DeepSeek，取自 div_model_config.vendor） */
    private String vendor;

    /**
     * 模型显示名：目录模式取目录 name；自定义模式取 customModel。
     * 客户端回显「当前使用：厂商 · 模型」用。
     */
    private String modelName;

    /** 自定义接入点（目录模式为目录 base_url 快照） */
    private String customBaseUrl;

    /** 自定义模型名（目录模式为目录 model_id 快照） */
    private String customModel;

    /** 掩码 sk-****abc4 */
    private String apiKeyMask;

    private Integer verified;

    private Integer enabled;

    /** 观星台 AI 解读总开关（0/1，取自 div_platform_setting.div_use_enabled）：关闭时客户端隐藏入口并拦截解读 */
    private Integer divUseEnabled;
}
