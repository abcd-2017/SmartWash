package com.smartwash.divination.vo;

import lombok.Data;

/**
 * 用户 BYOK 配置视图（仅掩码，无明文/密文）。
 */
@Data
public class UserApiConfigVo {

    private Long id;

    /** 平台预设供应商时显示名 */
    private String providerName;

    /** 自定义接入点 */
    private String customBaseUrl;

    private String customModel;

    /** 掩码 sk-****abc4 */
    private String apiKeyMask;

    private Integer verified;

    private Integer enabled;

    /** 平台 BYOK 开关（0/1，取自 div_platform_setting.byok_enabled）：关闭时客户端可隐藏入口 */
    private Integer byokEnabled;
}
