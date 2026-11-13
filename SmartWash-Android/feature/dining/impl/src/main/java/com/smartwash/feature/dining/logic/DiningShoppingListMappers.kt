package com.smartwash.feature.dining.logic

import com.google.gson.Gson
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningShoppingListSnapshot

/** 购买清单结果页模型：双视图共用的渲染单元（食材并集 / 按菜视图） */
data class DiningShoppingListResult(
    /** 按食材买菜视图：食材名并集去重（保序） */
    val ingredients: List<String>,
    /** 按菜备菜视图：每道菜及其选菜人（仅含被勾选过的菜） */
    val perRecipe: List<DiningRecipeShopping>,
)

/** 按菜备菜视图条目：一道菜及其全部选菜人 */
data class DiningRecipeShopping(
    /** 菜名 */
    val recipeName: String,
    /** 选菜人（按勾选顺序） */
    val selectors: List<DiningSelectorShopping>,
)

/** 选菜人条目：谁选的 + 个人备注 */
data class DiningSelectorShopping(
    /** 选菜人标识（脱敏手机号；缺失兜底「用户 {id}」） */
    val label: String,
    /** 个人备注（空串=未写备注） */
    val note: String,
    /** 是否当前用户 */
    val isMe: Boolean,
)

private val GSON = Gson()

/**
 * 解析购买清单快照 JSON（后端 dining_order.shopping_list 原文）。
 * null/空白/畸形 JSON 一律返回 null（结果页按「无清单」降级，不崩溃）。
 */
fun parseDiningShoppingList(raw: String?): DiningShoppingListSnapshot? {
    if (raw.isNullOrBlank()) return null
    return runCatching { GSON.fromJson(raw, DiningShoppingListSnapshot::class.java) }.getOrNull()
}

/**
 * 快照 → 结果页双视图模型（纯函数）：
 * - 选菜人标识取参与者脱敏手机号（[members]），缺失按「用户 {id}」兜底；
 * - [myUserId] 用于标记「我」的选菜（未取到为 null——不误判）。
 */
fun DiningShoppingListSnapshot.toShoppingListResult(
    members: List<DiningOrderDetailVo.OrderMember>,
    myUserId: Long?,
): DiningShoppingListResult {
    val labels = members.associate { it.safeUserId to it.safePhoneMasked }
    return DiningShoppingListResult(
        ingredients = safeIngredientNames,
        perRecipe = safePerRecipe.map { view ->
            DiningRecipeShopping(
                recipeName = view.safeRecipeName,
                selectors = view.safeSelectors.map { selector ->
                    DiningSelectorShopping(
                        label = labels[selector.safeUserId]?.takeIf { it.isNotBlank() }
                            ?: "用户 ${selector.safeUserId}",
                        note = selector.safeNote,
                        isMe = myUserId != null && selector.safeUserId == myUserId,
                    )
                },
            )
        },
    )
}
