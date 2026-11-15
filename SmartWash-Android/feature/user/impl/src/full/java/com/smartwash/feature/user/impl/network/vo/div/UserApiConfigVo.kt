package com.smartwash.feature.user.impl.network.vo.div

import androidx.annotation.Keep

/**
 * 用户解读模型配置视图（对应后端 vo/UserApiConfigVo，2026-11-11 契约重构版）。
 *
 * 仅回显掩码（apiKeyMask），无明文/密文；未配置时信封 data 为 null，
 * 端上以 null 表示「未配置，不可解读」（平台零兜底，解读统一 428）。
 *
 * 双模式（PUT /web/auth/div/settings/api 同口径）：
 * - 目录模式：modelConfigId 非空，vendor/modelName 取自平台目录（baseUrl 服务端持有，不下发用户输入）；
 * - 自定义模式：modelConfigId 为 null，customBaseUrl/customModel 生效（modelName=customModel）。
 *
 * 字段与观象台模块 DivinationApi.DivUserApiConfigVo（重复声明）逐字一致——
 * 跨域依赖铁律禁止 divination 依赖 user:impl，两端各自声明，改一处必同步另一处。
 */
@Keep
data class UserApiConfigVo(
    val id: Long? = null,
    /** 目录模式条目 id（自定义模式为 null） */
    val modelConfigId: Long? = null,
    /** 目录模式厂商名（一级分组；自定义模式为 null） */
    val vendor: String? = null,
    /** 目录模式=目录显示名，自定义模式=customModel */
    val modelName: String? = null,
    /** 自定义接入点地址（目录模式为 null） */
    val customBaseUrl: String? = null,
    /** 自定义模型名称 */
    val customModel: String? = null,
    /** 密钥掩码（如 sk-****abc4） */
    val apiKeyMask: String? = null,
    /** 服务端试呼通过标记（1=已验证） */
    val verified: Int? = null,
    /** 启用标记（1=参与解读调度） */
    val enabled: Int? = null,
    /** 观星台 AI 解读总开关（1=开启 / 0=关闭；替代原 byokEnabled 字段名，兼容旧响应缺失视为开启） */
    val divUseEnabled: Int? = null,
)
