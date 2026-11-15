package com.smartwash.feature.user.impl.network.entity.div

import androidx.annotation.Keep

/**
 * 解读模型配置保存/试呼请求体（对应后端 from/SaveUserApiFrom，2026-11-11 双模式契约）。
 *
 * 双模式二选一（PUT 保存与 POST test 共用本结构）：
 * - 目录模式：modelConfigId 必填（baseUrl/model 传 null，接入点/模型名服务端从目录取）；
 * - 自定义模式：baseUrl（https 且非内网）+ model 必填（modelConfigId 传 null）。
 * apiKey 两种模式均必填，仅经 HTTPS 请求体上行、由后端 AES-GCM 加密入库，
 * 端上不持久化、不进日志（服务端试呼通过才落库）。
 *
 * 命名与 JSON 契约逐字对齐（modelConfigId/baseUrl/model/apiKey，Gson 按字段名直映射），
 * 与既有 entity 无 @SerializedName 的风格保持一致。
 */
@Keep
data class SaveUserApiConfig(
    /** 目录模式：平台模型目录条目 id（自定义模式 null） */
    val modelConfigId: Long? = null,
    /** 自定义模式：OpenAI 兼容接入点（目录模式 null；服务端校验必须 https 且非内网地址） */
    val baseUrl: String? = null,
    /** 自定义模式：模型名称（目录模式 null；服务端以此发起 1-token 真实试呼） */
    val model: String? = null,
    /** API Key（两种模式均必填；明文仅在请求体内传输，响应与列表仅回掩码） */
    val apiKey: String,
)
