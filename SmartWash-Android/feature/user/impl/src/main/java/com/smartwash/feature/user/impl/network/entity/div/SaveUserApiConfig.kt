package com.smartwash.feature.user.impl.network.entity.div

import androidx.annotation.Keep

/**
 * 解读模型 BYOK 保存/试呼请求体（对应后端 from/SaveUserApiFrom）。
 *
 * 三字段服务端均必填（PUT 保存与 POST test 共用本结构）；apiKey 仅经 HTTPS
 * 请求体上行、由后端 AES-GCM 加密入库，端上不持久化、不进日志。
 */
@Keep
data class SaveUserApiConfig(
    /** OpenAI 兼容接入点（服务端校验必须 https 且非内网地址） */
    val baseUrl: String,
    /** 模型名称（服务端以此发起 1-token 真实试呼） */
    val model: String,
    /** API Key（明文仅在请求体内传输，响应与列表仅回掩码） */
    val apiKey: String,
)
