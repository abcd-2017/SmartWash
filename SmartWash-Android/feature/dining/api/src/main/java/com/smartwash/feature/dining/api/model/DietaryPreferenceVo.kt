package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 用户饮食偏好视图对象（GET/PUT /web/auth/dining/dietary-preference 载荷，契约 §2.1）。
 *
 * 字段 null = 默认值语义：端上兜底热量 2000 kcal / 钠 2000 mg；allergens 空列表 = 无忌口。
 * 同一数据类兼作 PUT 请求体（后端 DietaryPreferenceFrom 同名字段）：任一字段 null = 恢复默认
 * （allergens null=清空忌口）。Gson 默认不序列化 null 字段，后端 absent 即 null，语义等价。
 *
 * 忌口标签枚举：SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY（后端 Pattern 白名单）。
 */
@Keep
data class DietaryPreferenceVo(
    /** 忌口标签集合，null/缺失按空列表兜底（无忌口） */
    val allergens: List<String>? = null,
    /** 每日热量目标 kcal，null=默认 2000（有效区间 1200-4000） */
    val calorieTarget: Int? = null,
    /** 每日钠目标 mg，null=默认 2000（有效区间 1000-4000） */
    val sodiumTarget: Int? = null,
) {
    val safeAllergens: List<String> get() = allergens.orEmpty()

    companion object {
        /** 默认每日热量目标（kcal）：后端 null 语义的端上兜底值（契约 §2.1） */
        const val DEFAULT_CALORIE_TARGET = 2000

        /** 默认每日钠目标（mg）：后端 null 语义的端上兜底值（契约 §2.1） */
        const val DEFAULT_SODIUM_TARGET = 2000

        /** 热量目标有效区间（与后端 @Min/@Max、UI stepper 一致） */
        const val CALORIE_MIN = 1200
        const val CALORIE_MAX = 4000

        /** 钠目标有效区间（与后端 @Min/@Max、UI stepper 一致） */
        const val SODIUM_MIN = 1000
        const val SODIUM_MAX = 4000
    }
}
