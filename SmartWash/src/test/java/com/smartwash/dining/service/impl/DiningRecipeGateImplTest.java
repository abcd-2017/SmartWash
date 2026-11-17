package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * DiningRecipeGate 份热量口径锚点（P1 修复回归）：
 * calories_total 优先（HowToCook 整份口径，原值直返）；
 * 否则 calories_per_100g × 250g ÷ 100——每 100g 口径换算整份，
 * 200kcal/100g 的菜一份应得 500kcal 而非 50000（÷100 缺失回归锚）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiningRecipeGateImplTest {

    @Mock
    private DiningRecipeMapper recipeMapper;
    @Mock
    private DiningRecipeIngredientMapper recipeIngredientMapper;
    @Mock
    private DiningIngredientMapper ingredientMapper;

    private DiningRecipeGateImpl gate;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测环境无 MyBatis 容器：手动初始化实体 TableInfo，供 lambda 列名解析与 wrapper SQL 段物化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DiningRecipe.class);
        TableInfoHelper.initTableInfo(assistant, com.smartwash.dining.entity.DiningIngredient.class);
        TableInfoHelper.initTableInfo(assistant, com.smartwash.dining.entity.DiningRecipeIngredient.class);
    }

    @BeforeEach
    void setUp() {
        gate = new DiningRecipeGateImpl(recipeMapper, recipeIngredientMapper, ingredientMapper);
    }

    private DiningRecipe recipe(long id, BigDecimal caloriesTotal, BigDecimal caloriesPer100g) {
        DiningRecipe recipe = new DiningRecipe();
        recipe.setId(id);
        recipe.setCaloriesTotal(caloriesTotal);
        recipe.setCaloriesPer100g(caloriesPer100g);
        return recipe;
    }

    @Test
    @DisplayName("per100g 口径：200kcal/100g × 250g ÷ 100 = 500.00kcal/份（÷100 回归锚）")
    void servingCaloriesPer100gScaledByPortionOver100() {
        when(recipeMapper.selectList(any())).thenReturn(List.of(recipe(1L, null, new BigDecimal("200"))));
        Map<Long, BigDecimal> result = gate.getServingCalories(List.of(1L));
        assertEquals(0, result.get(1L).compareTo(new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("整份口径优先：calories_total=1051 直返，不做 250g 折算")
    void caloriesTotalPreferredWithoutPortionScaling() {
        when(recipeMapper.selectList(any())).thenReturn(List.of(recipe(2L, new BigDecimal("1051"), new BigDecimal("91"))));
        Map<Long, BigDecimal> result = gate.getServingCalories(List.of(2L));
        assertEquals(0, result.get(2L).compareTo(new BigDecimal("1051")));
    }

    @Test
    @DisplayName("两口径皆缺 = 热量未知，不进结果（不参与聚合）")
    void unknownCaloriesAbsentFromResult() {
        when(recipeMapper.selectList(any())).thenReturn(List.of(recipe(3L, null, null)));
        Map<Long, BigDecimal> result = gate.getServingCalories(List.of(3L));
        assertFalse(result.containsKey(3L));
    }

    @Test
    @DisplayName("空集合直返空 Map，不触发查询")
    void emptyIdsReturnEmptyMap() {
        Collection<Long> empty = Collections.emptyList();
        Map<Long, BigDecimal> result = gate.getServingCalories(empty);
        assertTrue(result.isEmpty());
    }
}
