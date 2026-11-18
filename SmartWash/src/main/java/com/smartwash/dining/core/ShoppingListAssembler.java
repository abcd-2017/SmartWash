package com.smartwash.dining.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 购买清单聚合纯函数（spec「关键行为-购买清单生成」+ 契约 §3.3 快照 v2）：
 * 提交点单时对入选菜谱按菜去重，取每道菜食材名的并集去重得到「按食材」清单，
 * 同时产出「按菜视图（菜 → 选了的人 + 备注）」（v1 字段，行为不变）；
 * v2 additive 扩展：按 (配料名, 归一单位) 分组聚合可加总量（kg→g、L→ml，其余原样）、
 * 已知热量菜的份热量汇总（totalCalories）与热量未知菜计数。
 *
 * <p>聚合口径（契约 §二/§3.3）：新模型下「菜单即已选清单」，selections 传空列表表示本单菜单全部入选
 * （按菜视图无「选菜人」）；兼容旧调用——selections 非空时仍只统计被勾选过的菜，未勾选的菜不进购买清单。
 * 同一配料在不同菜中单位不同（如 鸡蛋 575g 与 鸡蛋 2个）按 (名称, 归一单位) 分成两组分别求和；
 * 数量缺失（value 或 unit 为空）的配料只进名并集，不进数量聚合。</p>
 *
 * 纯函数约束：不依赖任何 Spring Bean / Mapper / 实体，输入输出全部为不可变 record 视图，
 * 菜谱食材由调用方（submit 提交点单接口）查库后转换传入；同一口径同时被单测锁定。
 */
public final class ShoppingListAssembler {

    private ShoppingListAssembler() {
    }

    /**
     * 菜谱食材供给视图（由调用方从菜谱库按本单菜单查得）。
     *
     * <p>v2 新增 ingredientAmounts（配料明细，关联表主数据源）与 calories（整份热量，
     * 调用方按「calories_total ?: calories_per_100g × 250g」口径算好传入，null=热量未知）；
     * ingredients（食材名 JSON 名列表）保留双写、老数据回落主源。提供 v1 三参构造兼容既有调用。</p>
     */
    public record RecipeIngredient(Long recipeId, String recipeName, List<String> ingredients,
                                   List<IngredientAmount> ingredientAmounts, BigDecimal calories) {

        /** v1 兼容构造：仅食材名列表（无配料明细、无热量），老调用方零改动 */
        public RecipeIngredient(Long recipeId, String recipeName, List<String> ingredients) {
            this(recipeId, recipeName, ingredients, List.of(), null);
        }
    }

    /** 配料明细视图：名称 + 数量（value/unit 可空 = 来源未给出，如「适量」） */
    public record IngredientAmount(String name, BigDecimal value, String unit) {
    }

    /** 配料聚合视图：按 (配料名, 归一单位) 分组的可加总量 */
    public record IngredientTotal(String name, BigDecimal totalValue, String unit, int dishCount,
                                  List<DishAmount> perDish) {
    }

    /** 分组内单菜贡献：哪道菜贡献了多少量（数值已按单位归一） */
    public record DishAmount(String recipeName, BigDecimal amountValue, String amountUnit) {
    }

    /** 逐菜热量视图（仅含已知热量的菜） */
    public record DishCalories(String recipeName, BigDecimal calories) {
    }

    /** 选菜记录视图（由调用方从 dining_order_item 查得） */
    public record Selection(Long recipeId, Long userId, String note) {
    }

    /**
     * 购买清单聚合结果。
     *
     * <p>v1 字段：ingredientNames（食材名并集）+ perRecipe（按菜视图），行为不变；
     * v2 字段：ingredientTotals（按配料聚合）、totalCalories（已知热量之和）、
     * calorieKnownDishCount / calorieUnknownDishCount（勾选菜中热量已知/未知数）、
     * perDishCalories（逐菜热量，仅已知菜）。</p>
     */
    public record ShoppingList(List<String> ingredientNames, List<RecipeView> perRecipe,
                               List<IngredientTotal> ingredientTotals, BigDecimal totalCalories,
                               int calorieKnownDishCount, int calorieUnknownDishCount,
                               List<DishCalories> perDishCalories) {
    }

    /**
     * 按菜视图：一道菜及其全部选菜人。recipeId 供端上从购买清单跳转菜谱详情；
     * 老快照无该字段，端上判 id 非空才可点击。
     */
    public record RecipeView(Long recipeId, String recipeName, List<SelectorView> selectors) {
    }

    /** 选菜人视图：谁选的 + 个人备注 */
    public record SelectorView(Long userId, String note) {
    }

    /**
     * @param recipes    本单菜单对应菜谱（顺序即菜单编排序，按菜视图/逐菜热量沿用该顺序）
     * @param selections 选菜记录；<b>空列表 = 本单菜单全部入选</b>（新模型：菜单即已选清单，按菜视图无选菜人），
     *                   非空时仅统计被勾选过的菜
     * @return v1 食材名并集去重（保序）+ 按菜视图；v2 配料聚合 + 热量汇总
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

        // 按菜视图按菜归组（组内按选菜记录顺序）；食材并集与聚合统一走菜单编排序（与勾选先后无关）
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
            selectorsByRecipe
                    .computeIfAbsent(selection.recipeId(), k -> new ArrayList<>())
                    .add(new SelectorView(selection.userId(), selection.note()));
        }
        // 入选集合：空选菜记录 = 整份菜单入选（新模型）；否则仅被勾选的菜
        boolean wholeMenu = safeSelections.isEmpty();
        Set<Long> selectedRecipeIds = wholeMenu
                ? new LinkedHashSet<>(recipeIndex.keySet())
                : new LinkedHashSet<>(selectorsByRecipe.keySet());

        // 食材名并集（LinkedHashSet 保序去重，名列表与配料明细两源合并）：
        // 按菜单编排序遍历入选的菜——同一份菜单无论勾选先后，快照顺序恒定（确定性契约）
        Set<String> ingredientNames = new LinkedHashSet<>();
        for (RecipeIngredient recipe : recipeIndex.values()) {
            if (!selectedRecipeIds.contains(recipe.recipeId())) {
                continue; // 未入选的菜：食材不进清单（与按菜视图同口径）
            }
            // 名列表/配料明细未填（null）安全跳过
            collectIngredientNames(recipe.ingredients(), ingredientNames);
            if (recipe.ingredientAmounts() != null) {
                for (IngredientAmount amount : recipe.ingredientAmounts()) {
                    if (amount != null && amount.name() != null && !amount.name().isBlank()) {
                        ingredientNames.add(amount.name().trim());
                    }
                }
            }
        }

        // 按菜视图沿用菜谱给定顺序（菜单编排序），只输出入选的菜；
        // 整份菜单入选时 selectors 为空列表（无「选菜人」）
        List<RecipeView> perRecipe = new ArrayList<>();
        recipeIndex.forEach((recipeId, recipe) -> {
            if (!selectedRecipeIds.contains(recipeId)) {
                return;
            }
            List<SelectorView> selectors = selectorsByRecipe.get(recipeId);
            perRecipe.add(new RecipeView(recipeId, recipe.recipeName(),
                    selectors == null ? List.of() : List.copyOf(selectors)));
        });

        // ===== v2：配料按 (名称, 归一单位) 分组求和 + 热量汇总（仅入选的菜，均按菜单编排序） =====
        List<IngredientTotal> ingredientTotals = aggregateIngredientTotals(recipeIndex, selectedRecipeIds);

        BigDecimal totalCalories = BigDecimal.ZERO;
        int knownCount = 0;
        int unknownCount = 0;
        List<DishCalories> perDishCalories = new ArrayList<>();
        for (Map.Entry<Long, RecipeIngredient> entry : recipeIndex.entrySet()) {
            if (!selectedRecipeIds.contains(entry.getKey())) {
                continue; // 未入选的菜不进热量统计（与食材并集同口径）
            }
            RecipeIngredient recipe = entry.getValue();
            if (recipe.calories() != null) {
                perDishCalories.add(new DishCalories(recipe.recipeName(), recipe.calories()));
                totalCalories = totalCalories.add(recipe.calories());
                knownCount++;
            } else {
                unknownCount++;
            }
        }

        return new ShoppingList(List.copyOf(ingredientNames), List.copyOf(perRecipe),
                List.copyOf(ingredientTotals), totalCalories, knownCount, unknownCount,
                List.copyOf(perDishCalories));
    }

    /** 食材名数组并入并集（null 元素/纯空白过滤、两侧空白裁剪），数组本身 null 安全跳过 */
    private static void collectIngredientNames(List<String> ingredients, Set<String> sink) {
        if (ingredients == null) {
            return;
        }
        for (String ingredient : ingredients) {
            if (ingredient != null && !ingredient.isBlank()) {
                sink.add(ingredient.trim());
            }
        }
    }

    /**
     * 配料聚合：对入选的菜，把其配料明细按 (名称, 归一单位) 分组求和。
     * kg→g、L→ml 换算 ×1000，其余单位原样保留；数量缺失（value/unit 空白）的配料不参与。
     * 按菜单编排序遍历（recipeIndex 即菜单序），聚合组与组内 perDish 顺序与勾选先后无关。
     */
    private static List<IngredientTotal> aggregateIngredientTotals(Map<Long, RecipeIngredient> recipeIndex,
                                                                   Set<Long> selectedRecipeIds) {
        // groupKey = name + "\u0001" + 归一单位 → 组内按菜累积（LinkedHashMap 按菜单序累积）
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        for (Map.Entry<Long, RecipeIngredient> entry : recipeIndex.entrySet()) {
            if (!selectedRecipeIds.contains(entry.getKey())) {
                continue; // 无人勾选的菜不参与聚合（与食材并集同口径）
            }
            RecipeIngredient recipe = entry.getValue();
            if (recipe.ingredientAmounts() == null) {
                continue;
            }
            for (IngredientAmount amount : recipe.ingredientAmounts()) {
                if (amount == null || amount.name() == null || amount.name().isBlank()) {
                    continue;
                }
                NormalizedQuantity quantity = normalize(amount.value(), amount.unit());
                if (quantity == null) {
                    continue; // 数量/单位缺失：只进名并集，不进数量聚合
                }
                String key = amount.name().trim() + '\u0001' + quantity.unit();
                groups.computeIfAbsent(key,
                                k -> new GroupAccumulator(amount.name().trim(), quantity.unit()))
                        .accumulate(recipe.recipeId(), recipe.recipeName(), quantity.value());
            }
        }

        List<IngredientTotal> totals = new ArrayList<>(groups.size());
        for (GroupAccumulator group : groups.values()) {
            totals.add(group.toTotal());
        }
        return totals;
    }

    /**
     * 数量归一：value 与 unit 均非空才可聚合；kg→g、L→ml ×1000（单位大小写不敏感），
     * 其余单位原样返回（契约 §3.3：kg→g、L→ml，其余原样）。
     */
    private static NormalizedQuantity normalize(BigDecimal value, String unit) {
        if (value == null || unit == null || unit.isBlank()) {
            return null;
        }
        String trimmedUnit = unit.trim();
        BigDecimal normalizedValue = value;
        String normalizedUnit = trimmedUnit;
        if ("kg".equalsIgnoreCase(trimmedUnit)) {
            normalizedValue = value.multiply(BigDecimal.valueOf(1000));
            normalizedUnit = "g";
        } else if ("l".equalsIgnoreCase(trimmedUnit)) {
            normalizedValue = value.multiply(BigDecimal.valueOf(1000));
            normalizedUnit = "ml";
        }
        return new NormalizedQuantity(normalizedValue, normalizedUnit);
    }

    /** 归一后的数量（值 + 展示单位） */
    private record NormalizedQuantity(BigDecimal value, String unit) {
    }

    /** 聚合组累积器：组内按 recipeId 去重合并（同菜重复明细行防御性求和），保菜谱顺序 */
    private static final class GroupAccumulator {
        private final String name;
        private final String unit;
        private final Map<Long, DishAccumulator> dishes = new LinkedHashMap<>();
        private BigDecimal total = BigDecimal.ZERO;

        private GroupAccumulator(String name, String unit) {
            this.name = name;
            this.unit = unit;
        }

        private void accumulate(Long recipeId, String recipeName, BigDecimal value) {
            DishAccumulator dish = dishes.computeIfAbsent(recipeId, k -> new DishAccumulator(recipeName));
            dish.value = dish.value.add(value);
            total = total.add(value);
        }

        private IngredientTotal toTotal() {
            List<DishAmount> perDish = new ArrayList<>(dishes.size());
            dishes.forEach((recipeId, dish) ->
                    perDish.add(new DishAmount(dish.recipeName, dish.value, unit)));
            return new IngredientTotal(name, total, unit, dishes.size(), List.copyOf(perDish));
        }
    }

    /** 组内单菜累积 */
    private static final class DishAccumulator {
        private final String recipeName;
        private BigDecimal value = BigDecimal.ZERO;

        private DishAccumulator(String recipeName) {
            this.recipeName = recipeName;
        }
    }
}
