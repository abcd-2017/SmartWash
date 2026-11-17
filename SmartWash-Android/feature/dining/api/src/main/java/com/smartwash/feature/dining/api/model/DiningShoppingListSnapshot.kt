package com.smartwash.feature.dining.api.model

import androidx.annotation.Keep

/**
 * 购买清单快照载荷（后端 dining_order.shopping_list JSON 的 Gson 结构，
 * 与后端 ShoppingListAssembler.ShoppingList 的 Jackson 序列化结果一一对应）。
 *
 * 生成口径（契约 §3.3 快照 v2 + spec「关键行为-购买清单生成」）：
 * - ingredientNames：按菜去重后，所选菜谱食材名的并集去重（保序）——v1 字段，行为不变；
 * - perRecipe：按菜视图（仅含被勾选过的菜，菜 → 选了的人 + 备注），顺序跟随本单菜单编排序——v1；
 * - ingredientTotals：按 (配料名, 归一单位) 分组聚合的可加总量（kg→g、L→ml，其余原样）——v2 additive；
 * - totalCalories：Σ已知热量菜的份热量（一份热量口径见 DiningRecipeVo KDoc）——v2；
 * - calorieKnownDishCount / calorieUnknownDishCount：勾选菜中热量已知/未知数——v2；
 * - perDishCalories：逐菜热量（仅已知热量菜）——v2。
 *
 * v2 向后兼容：老快照无 v2 字段 → Gson 反序列化为 null，消费方按「无 v2 数据」降级
 * （热量卡隐藏、按食材 tab 回落 ingredientNames 逐名渲染）。
 *
 * Gson 容错约定（api 模块统一）：字段可空 + 默认 null，消费方经 safe 系兜底。
 */
@Keep
data class DiningShoppingListSnapshot(
    /** 食材名并集去重（保序） */
    val ingredientNames: List<String>? = null,
    /** 按菜视图（仅含被勾选过的菜） */
    val perRecipe: List<RecipeView>? = null,
    /** v2：按 (配料名, 归一单位) 分组聚合的可加总量，null=老快照无 v2 数据 */
    val ingredientTotals: List<IngredientTotal>? = null,
    /** v2：已知热量菜的份热量之和（Kcal），null=老快照无 v2 数据 */
    val totalCalories: Double? = null,
    /** v2：热量已知菜的菜数，null=老快照无 v2 数据 */
    val calorieKnownDishCount: Int? = null,
    /** v2：热量未知菜的菜数（未计入总热量），null=老快照无 v2 数据 */
    val calorieUnknownDishCount: Int? = null,
    /** v2：逐菜热量（仅已知热量菜），null=老快照无 v2 数据 */
    val perDishCalories: List<DishCalories>? = null,
) {
    val safeIngredientNames: List<String> get() = ingredientNames.orEmpty()
    val safePerRecipe: List<RecipeView> get() = perRecipe.orEmpty()
    val safeIngredientTotals: List<IngredientTotal> get() = ingredientTotals.orEmpty()
    val safePerDishCalories: List<DishCalories> get() = perDishCalories.orEmpty()

    /** 是否携带 v2 扩展数据（老快照为 false，消费方据此降级渲染） */
    val hasV2Fields: Boolean
        get() = ingredientTotals != null || totalCalories != null || calorieKnownDishCount != null

    /** 配料聚合条目：按 (配料名, 归一单位) 分组的可加总量 */
    @Keep
    data class IngredientTotal(
        val name: String? = null,
        /** 聚合总量（单位见 [unit]；kg/L 已按后端归一为 g/ml），null=来源未给出 */
        val totalValue: Double? = null,
        /** 归一后单位（g/ml/个/…），null=未给出 */
        val unit: String? = null,
        /** 该配料出现的菜数（聚合行「N 道菜」徽标） */
        val dishCount: Int? = null,
        /** 组内单菜贡献（哪道菜贡献了多少量） */
        val perDish: List<DishAmount>? = null,
    ) {
        val safeName: String get() = name.orEmpty()
        val safeUnit: String get() = unit.orEmpty()
        val safeDishCount: Int get() = dishCount ?: 0
        val safePerDish: List<DishAmount> get() = perDish.orEmpty()
    }

    /** 聚合组内单菜贡献：哪道菜贡献了多少量（数值已按单位归一） */
    @Keep
    data class DishAmount(
        val recipeName: String? = null,
        val amountValue: Double? = null,
        val amountUnit: String? = null,
    ) {
        val safeRecipeName: String get() = recipeName.orEmpty()
        val safeAmountUnit: String get() = amountUnit.orEmpty()
    }

    /** 逐菜热量条目（仅已知热量的菜） */
    @Keep
    data class DishCalories(
        val recipeName: String? = null,
        val calories: Double? = null,
    ) {
        val safeRecipeName: String get() = recipeName.orEmpty()
    }

    /** 按菜视图条目：一道菜及其全部选菜人（v1 字段，行为不变） */
    @Keep
    data class RecipeView(
        val recipeName: String? = null,
        /** 选菜人列表（按勾选顺序） */
        val selectors: List<SelectorView>? = null,
    ) {
        val safeRecipeName: String get() = recipeName.orEmpty()
        val safeSelectors: List<SelectorView> get() = selectors.orEmpty()
    }

    /** 选菜人条目：谁选的 + 个人备注（v1 字段，行为不变） */
    @Keep
    data class SelectorView(
        val userId: Long? = null,
        /** 个人备注（如 不要辣），可空 */
        val note: String? = null,
    ) {
        val safeUserId: Long get() = userId ?: 0L
        val safeNote: String get() = note.orEmpty()
    }
}
