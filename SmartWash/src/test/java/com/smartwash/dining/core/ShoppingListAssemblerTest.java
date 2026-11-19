package com.smartwash.dining.core;

import com.smartwash.dining.core.ShoppingListAssembler.IngredientAmount;
import com.smartwash.dining.core.ShoppingListAssembler.IngredientTotal;
import com.smartwash.dining.core.ShoppingListAssembler.RecipeIngredient;
import com.smartwash.dining.core.ShoppingListAssembler.Selection;
import com.smartwash.dining.core.ShoppingListAssembler.ShoppingList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 购买清单聚合纯函数测试（spec：食材名并集去重 + 按菜视图）。
 * 纯函数无依赖，直接断言输入输出契约；submit 提交点单接口复用同一聚合口径。
 */
@DisplayName("ShoppingListAssembler 购买清单聚合测试")
class ShoppingListAssemblerTest {

    @Test
    @DisplayName("食材名并集去重：跨菜重复食材只出现一次，按首次出现顺序排列")
    void assemble_mergesIngredientsAcrossRecipes() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿", "鸡蛋", "葱")),
                new RecipeIngredient(2L, "凉拌西红柿", List.of("西红柿", "糖")));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, "多放葱"),
                new Selection(2L, 102L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        // 西红柿在两道菜中都出现，并集去重后只保留首次位置
        assertEquals(List.of("西红柿", "鸡蛋", "葱", "糖"), list.ingredientNames());
    }

    @Test
    @DisplayName("按菜视图：同一道菜多人勾选时合并为一组，选菜人按记录顺序")
    void assemble_groupsSelectorsByRecipe() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿", "鸡蛋")));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, "不要辣"),
                new Selection(1L, 102L, null),
                new Selection(1L, 103L, "加双份蛋"));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(1, list.perRecipe().size());
        ShoppingListAssembler.RecipeView view = list.perRecipe().get(0);
        assertEquals("番茄炒蛋", view.recipeName());
        assertEquals(3, view.selectors().size());
        assertEquals(101L, view.selectors().get(0).userId());
        assertEquals("不要辣", view.selectors().get(0).note());
        assertNull(view.selectors().get(1).note(), "未写备注的选菜记录 note 保持 null（清空备注语义）");
        assertEquals(103L, view.selectors().get(2).userId());
    }

    @Test
    @DisplayName("无人勾选的菜不进按菜视图，其食材也不进购买清单")
    void assemble_unselectedRecipe_excluded() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿", "鸡蛋")),
                new RecipeIngredient(2L, "土豆丝", List.of("土豆", "醋")));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿", "鸡蛋"), list.ingredientNames());
        assertEquals(1, list.perRecipe().size());
        assertEquals("番茄炒蛋", list.perRecipe().get(0).recipeName());
    }

    @Test
    @DisplayName("按菜视图顺序跟随菜谱给定顺序（菜单编排序），与选菜记录先后无关")
    void assemble_perRecipeView_followsRecipeOrder() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿")),
                new RecipeIngredient(2L, "凉拌黄瓜", List.of("黄瓜")));
        List<Selection> selections = List.of(
                new Selection(2L, 102L, null),
                new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals("番茄炒蛋", list.perRecipe().get(0).recipeName());
        assertEquals("凉拌黄瓜", list.perRecipe().get(1).recipeName());
    }

    @Test
    @DisplayName("选菜记录的菜不在菜谱集合内（防御脏数据）：忽略该记录，不聚合其食材")
    void assemble_unknownRecipeSelection_ignored() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿")));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(999L, 102L, "幽灵菜"));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿"), list.ingredientNames());
        assertEquals(1, list.perRecipe().size());
        assertEquals(1, list.perRecipe().get(0).selectors().size());
    }

    @Test
    @DisplayName("食材名空白过滤：null 与纯空白食材不进清单，食材名两侧空白裁剪")
    void assemble_blankIngredients_filtered() {
        List<RecipeIngredient> recipes = List.of(
                // Arrays.asList 允许 null 元素（List.of 不允许），模拟食材数组含 null 的脏数据
                new RecipeIngredient(1L, "番茄炒蛋", Arrays.asList(" 西红柿 ", "", null, "鸡蛋")));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿", "鸡蛋"), list.ingredientNames());
    }

    @Test
    @DisplayName("菜谱 ingredients 为 null（未填食材）：安全跳过，不影响其他菜聚合")
    void assemble_nullIngredientsRecipe_skipped() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "白灼菜心", null),
                new RecipeIngredient(2L, "番茄炒蛋", List.of("西红柿")));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(2L, 102L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿"), list.ingredientNames());
        assertEquals(2, list.perRecipe().size(), "未填食材的菜仍出现在按菜视图（有人勾选）");
        assertTrue(list.perRecipe().get(0).selectors().size() == 1);
    }

    @Test
    @DisplayName("空选菜记录 = 整份菜单入选：食材并集与按菜视图覆盖全部菜，按菜视图无选菜人")
    void assemble_noSelections_wholeMenuIncluded() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿", "鸡蛋")),
                new RecipeIngredient(2L, "凉拌黄瓜", List.of("黄瓜")));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, List.of());

        assertEquals(List.of("西红柿", "鸡蛋", "黄瓜"), list.ingredientNames());
        assertEquals(2, list.perRecipe().size(), "整份菜单全部进入按菜视图");
        assertEquals("番茄炒蛋", list.perRecipe().get(0).recipeName());
        assertTrue(list.perRecipe().get(0).selectors().isEmpty(), "新模型无选菜人");
        assertEquals("凉拌黄瓜", list.perRecipe().get(1).recipeName());
        assertTrue(list.perRecipe().get(1).selectors().isEmpty());
    }

    @Test
    @DisplayName("null 输入按空集合处理，不抛 NPE")
    void assemble_nullInputs_safe() {
        ShoppingList list = ShoppingListAssembler.assemble(null, null);
        assertTrue(list.ingredientNames().isEmpty());
        assertTrue(list.perRecipe().isEmpty());
    }

    // ==================== v2：配料数量聚合（契约 §3.3） ====================

    private IngredientAmount amount(String name, String value, String unit) {
        return new IngredientAmount(name, value == null ? null : new BigDecimal(value), unit);
    }

    private RecipeIngredient v2Recipe(long id, String name, List<IngredientAmount> amounts, String calories) {
        return new RecipeIngredient(id, name, List.of(), amounts,
                calories == null ? null : new BigDecimal(calories), "");
    }

    @Test
    @DisplayName("v2 聚合：同单位跨菜求和（575g+555g=1130g），dishCount=2，perDish 按菜谱顺序逐菜展开")
    void assembleV2_sumsSameUnitAcrossRecipes() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "西红柿炒鸡蛋", List.of(amount("鸡蛋", "575", "g")), null),
                v2Recipe(2L, "厚蛋烧", List.of(amount("鸡蛋", "555", "g")), null));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(2L, 102L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(1, list.ingredientTotals().size());
        IngredientTotal total = list.ingredientTotals().get(0);
        assertEquals("鸡蛋", total.name());
        assertEquals(0, new BigDecimal("1130").compareTo(total.totalValue()));
        assertEquals("g", total.unit());
        assertEquals(2, total.dishCount());
        assertEquals(2, total.perDish().size());
        assertEquals("西红柿炒鸡蛋", total.perDish().get(0).recipeName());
        assertEquals(0, new BigDecimal("575").compareTo(total.perDish().get(0).amountValue()));
        assertEquals("厚蛋烧", total.perDish().get(1).recipeName());
    }

    @Test
    @DisplayName("v2 聚合：kg→g、L→ml 归一后求和（1.5kg+200g=1700g），展示单位为归一后单位")
    void assembleV2_normalizesKgToGramsAndLitersToMl() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "红烧肉", List.of(amount("五花肉", "1.5", "kg")), null),
                v2Recipe(2L, "回锅肉", List.of(amount("五花肉", "200", "g")), null));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(2L, 102L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(1, list.ingredientTotals().size());
        IngredientTotal total = list.ingredientTotals().get(0);
        assertEquals(0, new BigDecimal("1700").compareTo(total.totalValue()), "1.5kg 应归一为 1500g 与 200g 求和");
        assertEquals("g", total.unit());
        // 升量纲同口径验证（L→ml）
        List<RecipeIngredient> liquid = List.of(
                v2Recipe(1L, "汤", List.of(amount("水", "2", "L")), null));
        ShoppingList liquidList = ShoppingListAssembler.assemble(liquid, List.of(new Selection(1L, 101L, null)));
        assertEquals("ml", liquidList.ingredientTotals().get(0).unit());
        assertEquals(0, new BigDecimal("2000").compareTo(liquidList.ingredientTotals().get(0).totalValue()));
    }

    @Test
    @DisplayName("v2 聚合：同名配料不同单位分成两组分别求和（鸡蛋 575g 与 鸡蛋 2个 互不混合）")
    void assembleV2_sameNameDifferentUnits_splitGroups() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "西红柿炒鸡蛋", List.of(amount("鸡蛋", "575", "g")), null),
                v2Recipe(2L, "茶叶蛋", List.of(amount("鸡蛋", "2", "个")), null));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(2L, 102L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(2, list.ingredientTotals().size());
        IngredientTotal byGram = list.ingredientTotals().get(0);
        assertEquals("g", byGram.unit());
        assertEquals(0, new BigDecimal("575").compareTo(byGram.totalValue()));
        IngredientTotal byCount = list.ingredientTotals().get(1);
        assertEquals("个", byCount.unit());
        assertEquals(0, new BigDecimal("2").compareTo(byCount.totalValue()));
        assertEquals(List.of("鸡蛋"), list.ingredientNames(), "名并集去重后只出现一次");
    }

    @Test
    @DisplayName("v2 聚合：数量缺失（value/unit 任一为空）的配料只进名并集，不进数量聚合")
    void assembleV2_missingQuantity_nameOnly() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "白灼菜心", List.of(
                        amount("盐", null, null),       // 全缺（适量）
                        amount("生抽", "5", null),      // 缺单位
                        amount("菜心", null, "g"),      // 缺数值
                        amount("菜心", "300", "g")),    // 同名同组与缺数值项并存
                        null));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertTrue(list.ingredientNames().containsAll(List.of("盐", "生抽", "菜心")),
                "缺数量的配料仍进名并集");
        assertEquals(1, list.ingredientTotals().size(), "只有完整 value+unit 的条目参与聚合");
        assertEquals("菜心", list.ingredientTotals().get(0).name());
        assertEquals(0, new BigDecimal("300").compareTo(list.ingredientTotals().get(0).totalValue()));
    }

    @Test
    @DisplayName("v2 热量：totalCalories=已知菜份热量之和，perDishCalories 仅含已知菜，未知菜单独计数")
    void assembleV2_caloriesAggregated_withUnknownCount() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "宫保鸡丁", null, "1051"),
                v2Recipe(2L, "凉拌黄瓜", null, "120.5"),
                v2Recipe(3L, "热量未知菜", null, null));
        List<Selection> selections = List.of(
                new Selection(1L, 101L, null),
                new Selection(2L, 102L, null),
                new Selection(3L, 103L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(0, new BigDecimal("1171.5").compareTo(list.totalCalories()));
        assertEquals(2, list.calorieKnownDishCount());
        assertEquals(1, list.calorieUnknownDishCount());
        assertEquals(2, list.perDishCalories().size());
        assertEquals("宫保鸡丁", list.perDishCalories().get(0).recipeName());
        assertEquals(0, new BigDecimal("1051").compareTo(list.perDishCalories().get(0).calories()));
    }

    @Test
    @DisplayName("v2 热量：只统计被勾选过的菜（与食材并集同源），未勾选菜不进热量也不进配料聚合")
    void assembleV2_unselectedRecipe_excludedFromTotalsAndCalories() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "宫保鸡丁", List.of(amount("鸡胸肉", "300", "g")), "1051"),
                v2Recipe(2L, "土豆丝", List.of(amount("土豆", "400", "g")), "352"));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(0, new BigDecimal("1051").compareTo(list.totalCalories()), "未勾选的土豆丝不计入");
        assertEquals(1, list.calorieKnownDishCount());
        assertEquals(0, list.calorieUnknownDishCount());
        assertEquals(1, list.ingredientTotals().size());
        assertEquals("鸡胸肉", list.ingredientTotals().get(0).name());
    }

    @Test
    @DisplayName("v1 兼容：三参构造（仅食材名列表）数量聚合落空；热量缺失的勾选菜按未知计数（v2 口径一致）")
    void assembleV1_compatibleConstructor_emptyV2Fields() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿", "鸡蛋")));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿", "鸡蛋"), list.ingredientNames(), "v1 名并集行为不变");
        assertEquals(1, list.perRecipe().size(), "v1 按菜视图行为不变");
        assertTrue(list.ingredientTotals().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(list.totalCalories()));
        assertEquals(0, list.calorieKnownDishCount());
        assertEquals(1, list.calorieUnknownDishCount(), "三参构造热量恒缺失，勾选菜按未知计数");
        assertTrue(list.perDishCalories().isEmpty());
    }

    @Test
    @DisplayName("v2 防御：null 明细条目/null 热量输入安全跳过，不抛 NPE")
    void assembleV2_nullEntries_safe() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", Arrays.asList("西红柿"),
                        Arrays.asList(null, amount("鸡蛋", "575", "g")), null, ""));
        List<Selection> selections = List.of(new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        assertEquals(List.of("西红柿", "鸡蛋"), list.ingredientNames());
        assertEquals(1, list.ingredientTotals().size());
        assertEquals(1, list.calorieUnknownDishCount(), "被勾选且热量 null 的菜计为未知");
        assertEquals(0, BigDecimal.ZERO.compareTo(list.totalCalories()));
    }

    @Test
    @DisplayName("v2 顺序：勾选顺序与菜单顺序相反时，名并集/配料聚合/逐菜热量仍按菜单编排序输出（快照确定性）")
    void assembleV2_outputOrder_followsMenuOrder_notSelectionOrder() {
        List<RecipeIngredient> recipes = List.of(
                v2Recipe(1L, "宫保鸡丁", List.of(amount("鸡胸肉", "300", "g"), amount("花生", "50", "g")), "1051"),
                v2Recipe(2L, "凉拌黄瓜", List.of(amount("黄瓜", "400", "g"), amount("鸡蛋", "2", "个")), "120.5"));
        // 勾选顺序故意与菜单顺序相反：黄瓜先被勾选
        List<Selection> selections = List.of(
                new Selection(2L, 102L, null),
                new Selection(1L, 101L, null));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, selections);

        // 名并集：按菜单编排序（宫保鸡丁的食材在前），与勾选先后无关
        assertEquals(List.of("鸡胸肉", "花生", "黄瓜", "鸡蛋"), list.ingredientNames(),
                "名并集必须跟随菜单顺序，而非首次勾选顺序");
        // 配料聚合组：菜单在前的菜的配料组在前
        assertEquals("鸡胸肉", list.ingredientTotals().get(0).name());
        assertEquals("黄瓜", list.ingredientTotals().get(2).name(), "第 3 组应是菜单第 2 道菜的配料");
        // 逐菜热量：菜单编排序
        assertEquals("宫保鸡丁", list.perDishCalories().get(0).recipeName());
        assertEquals("凉拌黄瓜", list.perDishCalories().get(1).recipeName());
        // 按菜视图顺序沿用菜单编排序（宫保鸡丁在前），组内选菜人保持勾选记录顺序（行为不变）
        assertEquals("宫保鸡丁", list.perRecipe().get(0).recipeName());
        assertEquals(101L, list.perRecipe().get(0).selectors().get(0).userId());
        assertEquals("凉拌黄瓜", list.perRecipe().get(1).recipeName());
        assertEquals(102L, list.perRecipe().get(1).selectors().get(0).userId());
    }
}
