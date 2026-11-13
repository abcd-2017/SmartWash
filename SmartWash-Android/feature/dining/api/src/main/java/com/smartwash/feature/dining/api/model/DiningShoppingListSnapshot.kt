package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 购买清单快照载荷（后端 dining_order.shopping_list JSON 的 Gson 结构，
 * 与后端 ShoppingListAssembler.ShoppingList 的 Jackson 序列化结果一一对应）。
 *
 * 生成口径（spec「关键行为-购买清单生成」）：
 * - ingredientNames：按菜去重后，所选菜谱 ingredients 的名字并集去重（保序）；
 * - perRecipe：按菜视图（仅含被勾选过的菜，菜 → 选了的人 + 备注），顺序跟随本单菜单编排序。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningShoppingListSnapshot(
    /** 食材名并集去重（保序） */
    val ingredientNames: List<String>? = null,
    /** 按菜视图（仅含被勾选过的菜） */
    val perRecipe: List<RecipeView>? = null,
) {
    val safeIngredientNames: List<String> get() = ingredientNames.orEmpty()
    val safePerRecipe: List<RecipeView> get() = perRecipe.orEmpty()

    /** 按菜视图条目：一道菜及其全部选菜人 */
    @Keep
    data class RecipeView(
        /** 菜名 */
        val recipeName: String? = null,
        /** 选菜人列表（按勾选顺序） */
        val selectors: List<SelectorView>? = null,
    ) {
        val safeRecipeName: String get() = recipeName.orEmpty()
        val safeSelectors: List<SelectorView> get() = selectors.orEmpty()
    }

    /** 选菜人条目：谁选的 + 个人备注 */
    @Keep
    data class SelectorView(
        /** 选菜人用户 ID */
        val userId: Long? = null,
        /** 个人备注（如 不要辣），可空 */
        val note: String? = null,
    ) {
        val safeUserId: Long get() = userId ?: 0L
        val safeNote: String get() = note.orEmpty()
    }
}
