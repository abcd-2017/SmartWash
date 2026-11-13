package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 新增菜谱请求体（POST /web/auth/dining/recipes/add，与后端 from/AddDiningRecipeFrom 字段对应）。
 *
 * 后端契约：name 必填（≤64 字符）；categoryId null 或 0=未分类（哨兵协议，GLOSSARY 10.10）、
 * >0 校验分类存在；ingredients ≤100 项、单项 ≤32 字符；steps ≤20000 字符；coverObjectKey
 * 为封面上传接口返回的对象键（可空=不带封面）。source=USER 与直上架由后端写入，端上不传。
 */
@Keep
data class AddDiningRecipeRequest(
    /** 菜名（必填） */
    val name: String,
    /** 所属分类 ID：null 或 0=未分类 */
    val categoryId: Long? = null,
    /** 食材名数组 */
    val ingredients: List<String>? = null,
    /** 做法步骤（纯文本） */
    val steps: String? = null,
    /** 封面对象键（封面上传接口返回），null=暂不设置 */
    val coverObjectKey: String? = null,
)
