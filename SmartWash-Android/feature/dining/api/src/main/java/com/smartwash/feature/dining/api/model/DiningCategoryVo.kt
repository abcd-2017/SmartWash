package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 菜谱分类视图对象（GET /web/auth/dining/categories/all 载荷，与后端 dining/vo/DiningCategoryVo 对应）。
 *
 * Gson 容错约定（api 模块统一）：字段全部可空 + 默认 null——Gson 反序列化缺字段或
 * 类型漂移时置 null 而非抛出，消费方一律经 safe 系计算属性取兜底值。
 */
@Keep
data class DiningCategoryVo(
    /** 分类 ID（筛选参数 categoryId） */
    val id: Long? = null,
    /** 分类名称（chip 文案） */
    val name: String? = null,
    /** 排序，越小越靠前（后端已按 sort/id 升序返回，端上保序渲染） */
    val sort: Int? = null,
) {
    val safeId: Long get() = id ?: 0L
    val safeName: String get() = name.orEmpty()
}
