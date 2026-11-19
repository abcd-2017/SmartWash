package com.smartwash.feature.dining.logic

import com.google.gson.Gson
import com.smartwash.feature.dining.api.model.DiningOrderDetailVo
import com.smartwash.feature.dining.api.model.DiningShoppingListSnapshot

/** 购买清单结果页模型：双视图共用的渲染单元（食材并集/聚合 / 按菜视图） */
data class DiningShoppingListResult(
    /** 按食材买菜视图：食材名并集去重（保序；v2 快照亦含聚合行的名字，勾选态按名对齐） */
    val ingredients: List<String>,
    /** 按菜备菜视图：每道菜及其选菜人（仅含被勾选过的菜） */
    val perRecipe: List<DiningRecipeShopping>,
    /**
     * v2：配料聚合行（按 (名称, 归一单位) 分组，kg/L 已由后端归一为 g/ml）。
     * null=老快照无 v2 数据，「按食材」tab 回落 [ingredients] 逐名渲染。
     */
    val ingredientTotals: List<DiningIngredientTotalUi>? = null,
    /** v2：已知热量菜的份热量之和（Kcal）；null=老快照无 v2 数据（热量卡隐藏） */
    val totalCalories: Double? = null,
    /** v2：热量未知菜数（提示「N 道菜热量未知，未计入」）；null=老快照无 v2 数据 */
    val calorieUnknownDishCount: Int? = null,
    /** 参与者人数（人数 stepper 默认值来源；本地态不落库） */
    val memberCount: Int = 0,
)

/** 配料聚合行 UI 模型（「按食材」tab v2 渲染单元：名称 总量单位 · N 道菜） */
data class DiningIngredientTotalUi(
    val name: String,
    /** 聚合总量（null=来源未给出，仅列名不显数量） */
    val totalValue: Double?,
    /** 归一后单位（g/ml/个/…；空串=未给出） */
    val unit: String,
    /** 该配料出现的菜数（0 隐藏「N 道菜」徽标） */
    val dishCount: Int,
)

/** 按菜备菜视图条目：一道菜及其全部选菜人 */
data class DiningRecipeShopping(
    /** 菜谱 ID（>0 时购买清单可点击跳菜谱详情看做法；老快照 0=不可跳转） */
    val recipeId: Long = 0L,
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
 * 快照 → 结果页双视图模型（纯函数，契约 §3.3 快照 v2 additive）：
 * - 选菜人标识取参与者脱敏手机号（[members]），缺失按「用户 {id}」兜底；
 * - [myUserId] 用于标记「我」的选菜（未取到为 null——不误判）；
 * - v2 字段（ingredientTotals/totalCalories/calorieUnknownDishCount）原样透传，
 *   老快照为 null → 消费方降级（热量卡隐藏、按食材 tab 回落名并集）；
 * - [memberCount] 取参与者人数（人数 stepper 默认值，本地态不落库）。
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
                recipeId = view.safeRecipeId,
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
        ingredientTotals = if (ingredientTotals == null) {
            null
        } else {
            safeIngredientTotals.map { total ->
                DiningIngredientTotalUi(
                    name = total.safeName,
                    totalValue = total.totalValue,
                    unit = total.safeUnit,
                    dishCount = total.safeDishCount,
                )
            }
        },
        totalCalories = totalCalories,
        calorieUnknownDishCount = calorieUnknownDishCount,
        memberCount = members.size,
    )
}
