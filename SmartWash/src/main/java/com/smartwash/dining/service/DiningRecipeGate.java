package com.smartwash.dining.service;

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

    /** 菜谱简要信息 */
    record RecipeBrief(Long id, String name, Long categoryId) {
    }
}
