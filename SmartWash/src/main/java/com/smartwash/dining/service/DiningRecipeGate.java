package com.smartwash.dining.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 点单订单对菜谱库的窄访问门面。
 * 点单核心只依赖菜谱的「上架存在性校验」与「名称/分类/食材读取」最小能力，
 * 不直接耦合完整菜谱服务（实现基于 DiningRecipeMapper，13 号票由 JdbcTemplate 直查切换）。
 */
public interface DiningRecipeGate {

    /**
     * 份量假设常量（g）：一份热量口径 = calories_total ?: calories_per_100g × 250，
     * 250g 为份量假设常量（契约 §二拍板，四端一致写死并注释）。
     */
    BigDecimal PORTION_GRAMS = new BigDecimal("250");

    /**
     * 校验菜谱集合全部「存在且上架」，否则抛业务异常（任一不满足即整体拒绝，不静默过滤）。
     * 用于创建订单 / 复用历史菜单时把关本单菜单范围（下架菜不得进入新订单）。
     */
    void assertAllOnShelf(Collection<Long> recipeIds);

    /**
     * 批量取菜谱简要信息（名称 / 分类），供订单详情 VO 与购买清单组装使用。
     *
     * @return recipeId → 简要信息；查询集合为空时返回空 Map，查不到的菜谱不出现在结果中
     */
    Map<Long, RecipeBrief> getBriefs(Collection<Long> recipeIds);

    /**
     * 批量取菜谱食材名数组（原样保序，不做并集去重——去重由 ShoppingListAssembler 统一负责），
     * 供结束点单生成购买清单快照使用。
     *
     * @return recipeId → 食材名数组；查询集合为空时返回空 Map，未填食材的菜谱对应空列表，
     *         查不到的菜谱不出现在结果中
     */
    Map<Long, List<String>> getIngredients(Collection<Long> recipeIds);

    /**
     * 批量取菜谱配料明细（dining_recipe_ingredient 关联表，行序按 id 升序=导入写入顺序），
     * 供结束点单购买清单 v2 数量聚合使用（契约 §3.3）。
     *
     * @return recipeId → 配料明细列表；查询集合为空时返回空 Map，无关联明细的老菜谱对应空列表
     *         （调用方回落 ingredients JSON 名列表，数量按缺失处理），查不到的菜谱不出现在结果中
     */
    Map<Long, List<IngredientDetail>> getIngredientAmounts(Collection<Long> recipeIds);

    /**
     * 批量取菜谱「一份热量」（份量口径 {@link #PORTION_GRAMS}：calories_total ?:
     * calories_per_100g × 250，两者都缺返回 null=热量未知），供结束点单热量汇总使用。
     *
     * @return recipeId → 一份热量（Kcal）；查询集合为空时返回空 Map，热量未知的菜谱不出现在结果中
     */
    Map<Long, BigDecimal> getServingCalories(Collection<Long> recipeIds);

    record RecipeBrief(Long id, String name, Long categoryId, String coverObjectKey) {
    }

    /** 菜谱配料明细视图：配料名 + 数量（value/unit 可空 = 来源未给出，如「适量」） */
    record IngredientDetail(String name, BigDecimal amountValue, String amountUnit) {
    }
}
