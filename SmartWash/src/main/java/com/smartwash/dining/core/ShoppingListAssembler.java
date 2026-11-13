package com.smartwash.dining.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 购买清单聚合纯函数（spec「关键行为-购买清单生成」）：
 * 结束点单时对所有选菜记录按菜去重，取每道菜食材名的并集去重得到「按食材」清单，
 * 同时产出「按菜视图（菜 → 选了的人 + 备注）」；不聚合数量、不换算单位（spec 有意简化）。
 *
 * 纯函数约束：不依赖任何 Spring Bean / Mapper / 实体，输入输出全部为不可变 record 视图，
 * 菜谱食材与选菜记录由调用方（11 号票结束点单接口）查库后转换传入；同一口径同时被单测锁定。
 */
public final class ShoppingListAssembler {

    private ShoppingListAssembler() {
    }

    /** 菜谱食材供给视图（由调用方从菜谱库按本单菜单查得） */
    public record RecipeIngredient(Long recipeId, String recipeName, List<String> ingredients) {
    }

    /** 选菜记录视图（由调用方从 dining_order_item 查得） */
    public record Selection(Long recipeId, Long userId, String note) {
    }

    /** 购买清单聚合结果：食材名并集 + 按菜视图 */
    public record ShoppingList(List<String> ingredientNames, List<RecipeView> perRecipe) {
    }

    /** 按菜视图：一道菜及其全部选菜人 */
    public record RecipeView(String recipeName, List<SelectorView> selectors) {
    }

    /** 选菜人视图：谁选的 + 个人备注 */
    public record SelectorView(Long userId, String note) {
    }

    /**
     * 聚合购买清单。
     *
     * @param recipes    本单菜单对应菜谱（顺序即菜单编排序，按菜视图沿用该顺序）
     * @param selections 全部选菜记录
     * @return 食材名并集去重（保序）+ 按菜视图（仅含被勾选过的菜）
     */
    public static ShoppingList assemble(List<RecipeIngredient> recipes, List<Selection> selections) {
        List<RecipeIngredient> safeRecipes = recipes == null ? List.of() : recipes;
        List<Selection> safeSelections = selections == null ? List.of() : selections;

        // recipeId → 菜谱视图，支撑选菜记录按菜归组；顺序沿用菜谱给定顺序
        Map<Long, RecipeIngredient> recipeIndex = new LinkedHashMap<>();
        for (RecipeIngredient recipe : safeRecipes) {
            if (recipe != null && recipe.recipeId() != null) {
                recipeIndex.putIfAbsent(recipe.recipeId(), recipe);
            }
        }

        // 食材名并集（LinkedHashSet 保序去重），按菜视图按菜归组（组内按选菜记录顺序）
        Set<String> ingredientNames = new LinkedHashSet<>();
        Map<Long, List<SelectorView>> selectorsByRecipe = new LinkedHashMap<>();
        for (Selection selection : safeSelections) {
            if (selection == null || selection.recipeId() == null) {
                continue;
            }
            RecipeIngredient recipe = recipeIndex.get(selection.recipeId());
            if (recipe == null) {
                // 防御脏数据：选菜记录指向的菜不在本单菜单集合内，忽略该记录
                continue;
            }
            // 食材并集：仅收集被勾选过的菜，未填食材（null）安全跳过
            if (recipe.ingredients() != null) {
                for (String ingredient : recipe.ingredients()) {
                    if (ingredient != null && !ingredient.isBlank()) {
                        ingredientNames.add(ingredient.trim());
                    }
                }
            }
            selectorsByRecipe
                    .computeIfAbsent(selection.recipeId(), k -> new ArrayList<>())
                    .add(new SelectorView(selection.userId(), selection.note()));
        }

        // 按菜视图沿用菜谱给定顺序（菜单编排序），只输出被勾选过的菜
        List<RecipeView> perRecipe = new ArrayList<>();
        recipeIndex.forEach((recipeId, recipe) -> {
            List<SelectorView> selectors = selectorsByRecipe.get(recipeId);
            if (selectors != null && !selectors.isEmpty()) {
                perRecipe.add(new RecipeView(recipe.recipeName(), List.copyOf(selectors)));
            }
        });

        return new ShoppingList(List.copyOf(ingredientNames), List.copyOf(perRecipe));
    }
}
