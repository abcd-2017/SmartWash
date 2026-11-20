package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningRecipeVo

/**
 * 创建订单页浏览态数据集快照（搜索态「双状态」契约的数据层）：
 *
 * 进入搜索前把浏览态（已加载分页）整体存为快照；退出搜索时零网络还原——浏览列表内容
 * 与搜索前逐项一致，UI 侧浏览滚动状态才能精确恢复到「原来那道菜」（而非按索引近似 +
 * 连环 load-more 追页）。快照只在关键词非空白期间被动持有，还原即丢弃。
 *
 * 还原规则：搜索期间唯一会被端上变更的字段是 [DiningRecipeVo.favorited]（收藏切换乐观
 * 更新当前可见列表），还原时以搜索结果的最新收藏态做字段级合并；其余字段（名称/食材/
 * 营养等）保持快照值，避免用搜索响应的另一份服务端快照覆盖浏览态数据。搜索结果未覆盖
 * 的菜在搜索期间不可见、收藏态不可能变化，原样保留。
 */
data class DiningBrowseCatalog(
    val recipes: List<DiningRecipeVo>,
    val page: Int,
    val hasMore: Boolean,
) {
    /**
     * 还原浏览列表：[latestStates] 为退出搜索时搜索结果列表的同 id VO（收藏态最新值）。
     */
    fun restoreRecipes(latestStates: Map<Long, DiningRecipeVo>): List<DiningRecipeVo> =
        recipes.map { vo ->
            latestStates[vo.safeId]?.let { latest -> vo.copy(favorited = latest.favorited) } ?: vo
        }
}
