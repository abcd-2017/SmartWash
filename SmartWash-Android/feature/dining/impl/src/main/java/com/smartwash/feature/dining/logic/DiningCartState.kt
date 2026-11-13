package com.smartwash.feature.dining.logic

import com.smartwash.feature.dining.api.model.DiningOrderDetailVo

/**
 * 点单页本地选菜状态（纯 Kotlin 状态类，JVM 单测锚点）。
 *
 * 只表达「当前用户选了哪些菜 + 每道菜的备注」（recipeId → note，note 空串=无备注）；
 * 共享购物车其他人的勾选不在此状态内——那些是服务端详情的只读投影，由页面渲染层合并。
 *
 * 本地交互即时生效（勾选/取消/改备注），写接口异步落库为最小接入（成功/失败记日志），
 * 版本冲突重放为 10 号票；本状态类是重放所需的最小事实（选中集合 + 备注）。
 */
data class DiningCartState(
    /** 当前用户选菜：recipeId → note（空串=无备注） */
    val selections: Map<Long, String> = emptyMap(),
) {
    /** 已选数量 */
    val count: Int get() = selections.size

    /** 是否已选该菜 */
    fun isSelected(recipeId: Long): Boolean = selections.containsKey(recipeId)

    /** 该菜的本地备注（未选/无备注返回空串） */
    fun noteOf(recipeId: Long): String = selections[recipeId].orEmpty()

    /**
     * 勾选/取消切换：未选 → 选中（备注空串）；已选 → 取消（连同备注移除）。
     */
    fun toggle(recipeId: Long): DiningCartState =
        if (selections.containsKey(recipeId)) {
            copy(selections = selections - recipeId)
        } else {
            copy(selections = selections + (recipeId to ""))
        }

    /**
     * 修改某道已选菜的备注：未选该菜时原样返回（备注不能脱离勾选存在）；
     * note 去空白后为空串表示清空备注但保留勾选。
     */
    fun withNote(recipeId: Long, note: String): DiningCartState {
        if (!selections.containsKey(recipeId)) return this
        return copy(selections = selections + (recipeId to note.trim()))
    }

    companion object {
        /**
         * 由详情选菜记录重建当前用户本地选菜状态（[myUserId] 为 null 时不识别任何选菜——
         * 未取到当前用户标识的降级，不误判他人选择为「我的」）。
         */
        fun fromItems(items: List<DiningOrderDetailVo.OrderItem>, myUserId: Long?): DiningCartState {
            if (myUserId == null) return DiningCartState()
            val selections = items
                .filter { it.safeUserId == myUserId }
                .associate { it.safeRecipeId to it.safeNote }
            return DiningCartState(selections)
        }
    }
}
