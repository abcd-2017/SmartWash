package com.smartwash.feature.dining.logic

/**
 * 共享购物车重放差分（纯 Kotlin，JVM 单测锚点）。
 *
 * 重放语义（spec「共享购物车并发」）：
 * - 本地意图（[DiningCartState]）是用户当前期望的选菜 + 备注；
 * - 服务端快照只取「我的选菜」（recipeId → note），他人选菜永不入此表；
 * - 版本冲突后先合并服务端最新快照（含他人变更），再以本地意图为基准计算差分，
 *   只重放本地意图与服务端不一致的部分——冲突窗口内他人的变更因此永不被本地旧态覆盖。
 */

/** 把服务端状态对齐到本地意图所需的最小写操作 */
sealed interface DiningCartReplayOp {
    /** 菜谱 ID */
    val recipeId: Long

    /** 本地意图选中而服务端没有：补勾（note 空串=无备注） */
    data class Add(override val recipeId: Long, val note: String) : DiningCartReplayOp

    /** 服务端仍挂着而本地意图已取消：取消选菜 */
    data class Remove(override val recipeId: Long) : DiningCartReplayOp

    /** 两边都选中但备注不一致：改备注（note 空串=清空备注） */
    data class UpdateNote(override val recipeId: Long, val note: String) : DiningCartReplayOp
}

/**
 * 计算重放差分：把 [serverMine] 对齐到 [intent] 所需的最少操作。
 * 顺序：补勾/改备注在前，取消在后。
 *
 * 入参契约：[serverMine] 只应包含当前用户的选菜记录。若误传他人记录，本函数会把他人记录
 * 当作「服务端我的」而产出 [DiningCartReplayOp.Remove]——调用方（状态机/仓库）必须只传当前用户条目。
 */
fun replayOperations(
    serverMine: Map<Long, String>,
    intent: DiningCartState,
): List<DiningCartReplayOp> {
    val ops = mutableListOf<DiningCartReplayOp>()
    for ((recipeId, note) in intent.selections) {
        val serverNote = serverMine[recipeId]
        if (serverNote == null) {
            ops += DiningCartReplayOp.Add(recipeId, note)
        } else if (serverNote != note) {
            ops += DiningCartReplayOp.UpdateNote(recipeId, note)
        }
    }
    for (recipeId in serverMine.keys) {
        if (!intent.selections.containsKey(recipeId)) {
            ops += DiningCartReplayOp.Remove(recipeId)
        }
    }
    return ops
}
