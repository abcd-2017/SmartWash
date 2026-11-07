package com.smartwash.feature.user.impl.network.vo.div

import androidx.annotation.Keep

/**
 * 平台启用模型目录项（对应后端 vo/ModelVo，GET web/auth/div/models）。
 *
 * 仅作 BYOK 表单「模型名称」输入的建议目录（点选回填 [modelId]）；
 * BYOK 本身不限于此目录，用户可输入任意 OpenAI 兼容模型名。
 */
@Keep
data class PlatformModelVo(
    val id: Long? = null,
    /** 展示名（如 DeepSeek V3） */
    val name: String? = null,
    /** 供应商标识 */
    val provider: String? = null,
    /** 请求体模型名（点选建议时回填输入框的值） */
    val modelId: String? = null,
    /** 平台调度优先级（端上仅展示用） */
    val priority: Int? = null,
    val active: Boolean? = null,
)
