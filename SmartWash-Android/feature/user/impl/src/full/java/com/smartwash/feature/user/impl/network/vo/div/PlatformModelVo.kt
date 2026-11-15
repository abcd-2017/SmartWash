package com.smartwash.feature.user.impl.network.vo.div

import androidx.annotation.Keep

/**
 * 平台启用模型目录厂商分组（对应后端 vo/VendorGroupVo，GET web/auth/div/models，2026-11-11 契约重构版）。
 *
 * 目录模式第一步=厂商（[vendor]），第二步=该厂商下的模型条目（点选回填 [PlatformModelVo.id] 作为
 * modelConfigId，接入点/模型名由服务端从目录取，用户不输 url）；
 * BYOK 自定义模式不受目录限制，用户可输入任意 OpenAI 兼容接入点与模型名。
 *
 * Gson 缺字段容错：各字段可空 / 列表默认空，旧响应缺失时不崩（展示按需降级）。
 */
@Keep
data class PlatformVendorGroupVo(
    /** 厂商名（目录模式第一步分组名） */
    val vendor: String? = null,
    /** 供应商标识 */
    val provider: String? = null,
    /** OpenAI 兼容接入点（目录模式由服务端取用，端上仅展示不回填输入框） */
    val baseUrl: String? = null,
    /** 平台调度优先级（端上仅展示用） */
    val priority: Int? = null,
    /** 该厂商下的模型条目（目录树二级） */
    val models: List<PlatformModelVo> = emptyList(),
)

/**
 * 平台启用模型目录项（对应后端 vo/ModelVo，2026-11-11 契约重构版：目录树二级）。
 *
 * vendor/provider/baseUrl 已上移到 [PlatformVendorGroupVo]；
 * 与观象台模块 DivinationApi.DivModelVo 字段逐字一致（跨域重复声明，改一处必同步另一处）。
 */
@Keep
data class PlatformModelVo(
    val id: Long,
    /** 展示名（如 DeepSeek V3） */
    val name: String? = null,
    /** 请求体模型名（端上展示用；目录模式保存传 id，服务端自行取用） */
    val modelId: String? = null,
    /** 平台调度优先级（端上仅展示用） */
    val priority: Int? = null,
    /** 启用标记（端上仅展示用） */
    val active: Boolean? = null,
)
