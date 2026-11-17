package com.smartwash.dining.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.smartwash.dining.entity.DiningIngredient;
import com.smartwash.dining.entity.DiningRecipe;
import com.smartwash.dining.entity.DiningRecipeIngredient;
import com.smartwash.dining.mapper.DiningIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeIngredientMapper;
import com.smartwash.dining.mapper.DiningRecipeMapper;
import com.smartwash.dining.service.DiningRecipeGate;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 点单订单对菜谱库的窄访问门面实现（13 号票由 JdbcTemplate 直查切换为正式 Mapper）：
 * 上架口径 dining_recipe.status=1（[DiningRecipe.STATUS_ON_SHELF]）。
 * 点单域只依赖本窄契约，不耦合完整菜谱服务，后续若菜谱域服务面扩展也不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiningRecipeGateImpl implements DiningRecipeGate {

    private final DiningRecipeMapper recipeMapper;

    /** 菜谱配料关联 Mapper（v2 购买清单数量聚合主数据源） */
    private final DiningRecipeIngredientMapper recipeIngredientMapper;

    /** 配料库 Mapper（关联行配料名回填） */
    private final DiningIngredientMapper ingredientMapper;

    @Override
    public void assertAllOnShelf(Collection<Long> recipeIds) {
        List<Long> distinct = distinctIds(recipeIds);
        if (distinct.isEmpty()) {
            throw new CustomExceptions("本单菜单不能为空");
        }
        Long onShelfCount = recipeMapper.selectCount(new LambdaQueryWrapper<DiningRecipe>()
                .in(DiningRecipe::getId, distinct)
                .eq(DiningRecipe::getStatus, DiningRecipe.STATUS_ON_SHELF));
        if (onShelfCount == null || onShelfCount < distinct.size()) {
            // 任一菜谱不存在或已下架即整体拒绝：不静默过滤，避免端上看到的菜单与服务端入库范围不一致
            log.info("创建点单菜单校验失败, 请求菜谱: {}, 上架菜谱数: {}", distinct, onShelfCount);
            throw new CustomExceptions("部分菜谱不存在或已下架，无法加入本单菜单");
        }
    }

    @Override
    public Map<Long, RecipeBrief> getBriefs(Collection<Long> recipeIds) {
        List<Long> distinct = distinctIds(recipeIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DiningRecipe> recipes = recipeMapper.selectList(new LambdaQueryWrapper<DiningRecipe>()
                .in(DiningRecipe::getId, distinct)
                .select(DiningRecipe::getId, DiningRecipe::getName, DiningRecipe::getCategoryId));
        Map<Long, RecipeBrief> result = new LinkedHashMap<>();
        for (DiningRecipe recipe : recipes) {
            result.put(recipe.getId(), new RecipeBrief(recipe.getId(), recipe.getName(), recipe.getCategoryId()));
        }
        return result;
    }

    @Override
    public Map<Long, List<String>> getIngredients(Collection<Long> recipeIds) {
        List<Long> distinct = distinctIds(recipeIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DiningRecipe> recipes = recipeMapper.selectList(new LambdaQueryWrapper<DiningRecipe>()
                .in(DiningRecipe::getId, distinct)
                .select(DiningRecipe::getId, DiningRecipe::getIngredients));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (DiningRecipe recipe : recipes) {
            result.put(recipe.getId(), parseIngredients(recipe.getIngredients()));
        }
        return result;
    }

    @Override
    public Map<Long, List<IngredientDetail>> getIngredientAmounts(Collection<Long> recipeIds) {
        List<Long> distinct = distinctIds(recipeIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        // 关联行按 recipe_id, id 升序：同菜内保持导入写入顺序，跨菜保持菜谱 id 序（调用方按菜归组）
        List<DiningRecipeIngredient> relations = recipeIngredientMapper.selectList(
                new LambdaQueryWrapper<DiningRecipeIngredient>()
                        .in(DiningRecipeIngredient::getRecipeId, distinct)
                        .orderByAsc(DiningRecipeIngredient::getRecipeId)
                        .orderByAsc(DiningRecipeIngredient::getId));
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量回填配料名（关联表只存 ingredient_id，名称是聚合与展示的主键外语义）
        List<Long> ingredientIds = relations.stream()
                .map(DiningRecipeIngredient::getIngredientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> nameById = ingredientIds.isEmpty() ? Collections.emptyMap()
                : ingredientMapper.selectBatchIds(ingredientIds).stream()
                .collect(Collectors.toMap(DiningIngredient::getId, DiningIngredient::getName, (a, b) -> a));
        Map<Long, List<IngredientDetail>> result = new LinkedHashMap<>();
        for (DiningRecipeIngredient relation : relations) {
            String name = nameById.get(relation.getIngredientId());
            if (name == null) {
                // 防御脏数据：关联指向的配料已被物理删除，跳过该行（不影响其他配料聚合）
                log.warn("菜谱配料关联指向不存在的配料, relationId: {}, ingredientId: {}",
                        relation.getId(), relation.getIngredientId());
                continue;
            }
            result.computeIfAbsent(relation.getRecipeId(), k -> new ArrayList<>())
                    .add(new IngredientDetail(name, relation.getAmountValue(), relation.getAmountUnit()));
        }
        return result;
    }

    @Override
    public Map<Long, BigDecimal> getServingCalories(Collection<Long> recipeIds) {
        List<Long> distinct = distinctIds(recipeIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DiningRecipe> recipes = recipeMapper.selectList(new LambdaQueryWrapper<DiningRecipe>()
                .in(DiningRecipe::getId, distinct)
                .select(DiningRecipe::getId, DiningRecipe::getCaloriesTotal, DiningRecipe::getCaloriesPer100g));
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (DiningRecipe recipe : recipes) {
            BigDecimal serving = servingCalories(recipe.getCaloriesTotal(), recipe.getCaloriesPer100g());
            if (serving != null) {
                result.put(recipe.getId(), serving);
            }
        }
        return result;
    }

    /**
     * 一份热量口径（契约 §二，四端一致写死）：calories_total 优先（HowToCook 整份口径）；
     * 否则 calories_per_100g × {@link DiningRecipeGate#PORTION_GRAMS} ÷ 100（每 100g 口径换算
     * 整份：200kcal/100g × 250g = 500kcal/份）；
     * 两者都缺返回 null = 热量未知，不参与热量聚合。
     */
    private BigDecimal servingCalories(BigDecimal caloriesTotal, BigDecimal caloriesPer100g) {
        if (caloriesTotal != null) {
            return caloriesTotal;
        }
        if (caloriesPer100g != null) {
            return caloriesPer100g.multiply(PORTION_GRAMS)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** 食材 JSON 文本 → 列表（未填/解析失败按空列表，不阻断购买清单生成） */
    private List<String> parseIngredients(String ingredientsJson) {
        if (ingredientsJson == null || ingredientsJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.parseObject(ingredientsJson, new TypeReference<List<String>>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("菜谱食材 JSON 解析失败，按空列表返回, ingredients: {}", ingredientsJson, e);
            return List.of();
        }
    }

    private List<Long> distinctIds(Collection<Long> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return Collections.emptyList();
        }
        return recipeIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }
}
