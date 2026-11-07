package com.smartwash.feature.user.impl.network.vo.div

import androidx.annotation.Keep

/**
 * 用户 BYOK 配置视图（对应后端 vo/UserApiConfigVo）。
 *
 * 仅回显掩码（apiKeyMask），无明文/密文；未配置时信封 data 为 null，
 * 端上以 null 表示「未配置，使用平台默认模型」。
 */
@Keep
data class UserApiConfigVo(
    val id: Long? = null,
    /** 平台预设供应商时显示名（自定义接入点场景为 null） */
    val providerName: String? = null,
    /** 自定义接入点地址 */
    val customBaseUrl: String? = null,
    /** 自定义模型名称 */
    val customModel: String? = null,
    /** 密钥掩码（如 sk-****abc4） */
    val apiKeyMask: String? = null,
    /** 服务端试呼通过标记（1=已验证） */
    val verified: Int? = null,
    /** 启用标记（1=参与解读调度） */
    val enabled: Int? = null,
    /** 平台级 BYOK 开关（1=开启 / 0=关闭；additive 新字段，兼容旧响应缺失视为开启） */
    val byokEnabled: Int? = null,
)
