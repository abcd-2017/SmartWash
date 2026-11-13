package com.smartwash.dining.core;

import com.smartwash.dining.core.ShoppingListAssembler.RecipeIngredient;
import com.smartwash.dining.core.ShoppingListAssembler.Selection;
import com.smartwash.dining.core.ShoppingListAssembler.ShoppingList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 购买清单聚合纯函数测试（spec：食材名并集去重 + 按菜视图，不聚合数量、不换算单位）。
 * 纯函数无依赖，直接断言输入输出契约；结束点单接口（11 号票）复用同一聚合口径。
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
    @DisplayName("空选菜记录：清单与按菜视图均为空")
    void assemble_noSelections_emptyResult() {
        List<RecipeIngredient> recipes = List.of(
                new RecipeIngredient(1L, "番茄炒蛋", List.of("西红柿")));

        ShoppingList list = ShoppingListAssembler.assemble(recipes, List.of());

        assertTrue(list.ingredientNames().isEmpty());
        assertTrue(list.perRecipe().isEmpty());
    }

    @Test
    @DisplayName("null 输入按空集合处理，不抛 NPE")
    void assemble_nullInputs_safe() {
        ShoppingList list = ShoppingListAssembler.assemble(null, null);
        assertTrue(list.ingredientNames().isEmpty());
        assertTrue(list.perRecipe().isEmpty());
    }
}
