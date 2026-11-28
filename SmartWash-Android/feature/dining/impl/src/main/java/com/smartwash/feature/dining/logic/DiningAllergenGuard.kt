package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo

/**
 * 吃啥忌口警示内核（纯 Kotlin，无 Android/Compose 依赖，JVM 单测锚点）。
 *
 * 口径（契约 §三.1）：把「选中菜的过敏原集合」与「用户忌口标签」求交，非空即命中，
 * 由页面渲染「含忌口：<忌口名>（<菜名>）」红色警示条。
 *
 * - 过敏原/忌口均为后端枚举码（SEAFOOD/NUT/GLUTEN/DAIRY/EGG/SOY/SPICY），比较前统一
 *   去空白 + 大写，避免大小写/空白漂移导致漏报；
 * - 同一道菜命中多个忌口时逐个产出（页面逐行渲染），同一码在一道菜内去重；
 * - 用户未设忌口（空集）直接返回空命中，不做无谓遍历；
 * - 端上只负责「已登记过敏原」的交集，生僻配料后端为 NULL 的不参与（覆盖率风险见契约 §五.3）。
 */
object DiningAllergenGuard {

    /** 忌口标签枚举（与后端 Pattern 白名单、设置页 7 个多选 chip 同源） */
    val ALLERGEN_CODES: List<String> =
        listOf("SEAFOOD", "NUT", "GLUTEN", "DAIRY", "EGG", "SOY", "SPICY")

    /** 选中菜的过敏原事实（页面由选中 VO + 详情缓存的 allergens 组装） */
    data class SelectedDish(
        val recipeId: Long,
        val recipeName: String,
        val allergens: Set<String>,
    )

    /** 单条命中：哪道菜命中了哪个忌口（[allergen] 为归一化后的枚举码，UI 映射中文文案） */
    data class Hit(
        val recipeId: Long,
        val recipeName: String,
        val allergen: String,
    )

    /** 求交命中（保选中菜顺序；用户忌口为空返回空表） */
    fun hits(dishes: List<SelectedDish>, userAllergens: Set<String>): List<Hit> {
        if (userAllergens.isEmpty()) return emptyList()
        val normalizedUser = normalizeAllergens(userAllergens)
        if (normalizedUser.isEmpty()) return emptyList()
        return dishes.flatMap { dish ->
            val normalizedDish = normalizeAllergens(dish.allergens)
            normalizedDish.filter { it in normalizedUser }
                .map { Hit(dish.recipeId, dish.recipeName, it) }
        }
    }

    /**
     * 聚合本单成员忌口标签（去重保序），供分享页展示「众口」提示。
     * 只聚合不关联个人，避免暴露单个成员的健康偏好。
     */
    fun summarizeMemberAllergens(members: List<DiningOrderDetailVo.OrderMember>): List<String> =
        members.flatMap { it.safeAllergens }.distinct()

    /** 归一化：去空白 + 大写 + 去重（脏数据不抛出；空串剔除） */
    fun normalizeAllergens(codes: Collection<String>): List<String> =
        codes.mapNotNull { normalizeAllergen(it) }.distinct()

    /** 单个码归一化：null/空白返回 null，其余去空白 + 大写 */
    fun normalizeAllergen(code: String?): String? =
        code?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
}
