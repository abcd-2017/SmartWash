package com.smartwash.dining.support;

import com.smartwash.dining.entity.DiningIngredient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 配料过敏原启发命中表测试（契约 §2.2）。
 * 逐标签断言代表名命中、未命中兜底 NULL、多命中按契约顺序取优先标签。
 */
@DisplayName("IngredientAllergenResolver 过敏原启发命中表")
class IngredientAllergenResolverTest {

    @Test
    @DisplayName("海鲜类：鱼/虾/蟹/贝/海鲜/鱿 全部命中 SEAFOOD")
    void seafoodHits() {
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("鱼"));
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("虾"));
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("蟹"));
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("贝"));
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("海鲜"));
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("鱿鱼"));
    }

    @Test
    @DisplayName("坚果类：花生/坚果/杏仁/核桃/腰果/榛 全部命中 NUT")
    void nutHits() {
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("花生"));
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("坚果"));
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("杏仁"));
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("核桃"));
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("腰果"));
        assertEquals(DiningIngredient.ALLERGEN_NUT, IngredientAllergenResolver.resolve("榛子"));
    }

    @Test
    @DisplayName("麸质类：面/麸/面包 命中 GLUTEN")
    void glutenHits() {
        assertEquals(DiningIngredient.ALLERGEN_GLUTEN, IngredientAllergenResolver.resolve("面"));
        assertEquals(DiningIngredient.ALLERGEN_GLUTEN, IngredientAllergenResolver.resolve("麸"));
        assertEquals(DiningIngredient.ALLERGEN_GLUTEN, IngredientAllergenResolver.resolve("面包"));
    }

    @Test
    @DisplayName("乳制品类：奶/乳/酪/芝士 命中 DAIRY")
    void dairyHits() {
        assertEquals(DiningIngredient.ALLERGEN_DAIRY, IngredientAllergenResolver.resolve("牛奶"));
        assertEquals(DiningIngredient.ALLERGEN_DAIRY, IngredientAllergenResolver.resolve("酸奶"));
        assertEquals(DiningIngredient.ALLERGEN_DAIRY, IngredientAllergenResolver.resolve("奶酪"));
        assertEquals(DiningIngredient.ALLERGEN_DAIRY, IngredientAllergenResolver.resolve("芝士"));
    }

    @Test
    @DisplayName("蛋类：蛋/鸡蛋 命中 EGG")
    void eggHits() {
        assertEquals(DiningIngredient.ALLERGEN_EGG, IngredientAllergenResolver.resolve("蛋"));
        assertEquals(DiningIngredient.ALLERGEN_EGG, IngredientAllergenResolver.resolve("鸡蛋"));
    }

    @Test
    @DisplayName("大豆类：豆/酱油/豉/味噌 命中 SOY")
    void soyHits() {
        assertEquals(DiningIngredient.ALLERGEN_SOY, IngredientAllergenResolver.resolve("豆腐"));
        assertEquals(DiningIngredient.ALLERGEN_SOY, IngredientAllergenResolver.resolve("酱油"));
        assertEquals(DiningIngredient.ALLERGEN_SOY, IngredientAllergenResolver.resolve("豆豉"));
        assertEquals(DiningIngredient.ALLERGEN_SOY, IngredientAllergenResolver.resolve("味噌"));
    }

    @Test
    @DisplayName("辛辣类：辣/椒/芥末 命中 SPICY")
    void spicyHits() {
        assertEquals(DiningIngredient.ALLERGEN_SPICY, IngredientAllergenResolver.resolve("辣椒"));
        assertEquals(DiningIngredient.ALLERGEN_SPICY, IngredientAllergenResolver.resolve("花椒"));
        assertEquals(DiningIngredient.ALLERGEN_SPICY, IngredientAllergenResolver.resolve("芥末"));
    }

    @Test
    @DisplayName("未命中兜底 NULL：无关键词常见配料（防「果」等单字误伤）")
    void noHitReturnsNull() {
        assertNull(IngredientAllergenResolver.resolve("西红柿"));
        assertNull(IngredientAllergenResolver.resolve("葱"));
        assertNull(IngredientAllergenResolver.resolve("蒜"));
        assertNull(IngredientAllergenResolver.resolve("盐"));
        assertNull(IngredientAllergenResolver.resolve("白糖"));
        assertNull(IngredientAllergenResolver.resolve("苹果"), "「果」仅为坚果关键词的一部分，不得单字误伤");
    }

    @Test
    @DisplayName("多命中按契约顺序取优先标签（海鲜 → 坚果 → 麸质 → 乳制品 → 蛋 → 大豆 → 辛辣）")
    void multiHitPriority() {
        assertEquals(DiningIngredient.ALLERGEN_SEAFOOD, IngredientAllergenResolver.resolve("海鲜酱"));
        assertEquals(DiningIngredient.ALLERGEN_GLUTEN, IngredientAllergenResolver.resolve("鸡蛋面"),
                "麸质优先级高于蛋类");
    }

    @Test
    @DisplayName("null/空白配料名返回 null")
    void blankReturnsNull() {
        assertNull(IngredientAllergenResolver.resolve(null));
        assertNull(IngredientAllergenResolver.resolve(""));
        assertNull(IngredientAllergenResolver.resolve("   "));
    }
}
