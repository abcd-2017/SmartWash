package com.smartwash.feature.dining.logic

/**
 * 购买清单文本导出（纯函数，JVM 单测锚点）。
 *
 * 场景：买菜时把清单发给家人/自己对着买。文本包含订单标题、「按食材买菜」勾选态
 * （[x] 已买 / [ ] 未买）与「按菜备菜」的选菜人（含备注），不依赖 Android 资源，
 * 便于跨端（鸿蒙）复用同一口径。
 */
fun buildShoppingListText(
    orderTitle: String,
    result: DiningShoppingListResult,
    checked: Set<String>,
): String = buildString {
    append("吃啥 · 购买清单")
    if (orderTitle.isNotBlank()) append("\n订单：").append(orderTitle)

    append("\n\n【按食材买菜】")
    if (result.ingredients.isEmpty()) {
        append("\n（无）")
    } else {
        result.ingredients.forEach { name ->
            append("\n").append(if (name in checked) "[x] " else "[ ] ").append(name)
        }
    }

    append("\n\n【按菜备菜】")
    if (result.perRecipe.isEmpty()) {
        append("\n（无）")
    } else {
        result.perRecipe.forEach { recipe ->
            append("\n- ").append(recipe.recipeName)
            if (recipe.selectors.isNotEmpty()) {
                val who = recipe.selectors.joinToString("、") { selector ->
                    if (selector.note.isNotBlank()) "${selector.label}（${selector.note}）" else selector.label
                }
                append("：").append(who)
            }
        }
    }
}
