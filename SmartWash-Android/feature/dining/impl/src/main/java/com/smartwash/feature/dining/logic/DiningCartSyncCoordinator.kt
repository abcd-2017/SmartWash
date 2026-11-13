package com.smartwash.feature.dining.logic

/**
 * 共享购物车同步状态机（纯 Kotlin，无协程/Android 依赖，JVM 单测锚点）。
 *
 * 状态 = 服务端真相（[serverVersion] + [serverMine]）+ 本地意图（[cart]）+ [needsRefresh]。
 * 事件 = 用户操作 / 写结果 / 服务端快照；输出 = 待写差分（[pendingWrite]）/ 重放差分（[onSnapshot]）/
 * 本地回滚（[onWriteFailure]）。
 *
 * 关键不变式：
 * - [serverMine] 只含当前用户条目，重放永不覆盖他人变更；
 * - 写响应不回传新 version，故写成功后 [needsRefresh]=true，须先拉取最新再发下一笔；
 * - 版本冲突（409）保留本地意图，待快照在新基准上重放；
 * - 写失败挂起回滚，待拉取最新快照后按最新真相重置本地意图（失败写不前进 version）。
 */
class DiningCartSyncCoordinator {

    /** 服务端最新 version（写操作携带的乐观并发基准） */
    var serverVersion: Long = 0L
        private set

    /** 服务端当前认定「我的选菜」：recipeId → note（他人选菜永不入此表） */
    var serverMine: Map<Long, String> = emptyMap()
        private set

    /** 本地意图（用户即时操作后的期望状态） */
    var cart: DiningCartState = DiningCartState()
        private set

    /** 是否需要先拉取最新详情才能发下一次写（写响应不回传新 version） */
    var needsRefresh: Boolean = false
        private set

    /**
     * 写失败挂起的回滚目标：失败写不前进 version，故不能在失败瞬间回滚
     * （此时 [serverMine] 可能仍是旧真相）；待拉取最新快照后按最新真相回滚本地意图。
     */
    private var pendingRollbackRecipeId: Long? = null

    /** 首载：服务端真相即本地意图（无本地未决操作） */
    fun onInitialSnapshot(version: Long, mine: Map<Long, String>) {
        serverVersion = version
        serverMine = mine
        cart = DiningCartState(mine)
        needsRefresh = false
        pendingRollbackRecipeId = null
    }

    /**
     * 合并服务端快照（含他人变更），返回需重放的本地差分。
     * 版本回退（过期快照）时丢弃；版本相同或前进时应用——写失败不前进 version，
     * 故失败后的全量刷新必须能落地（等同 version 也应用），随后执行挂起回滚。
     */
    fun onSnapshot(version: Long, mine: Map<Long, String>): List<DiningCartReplayOp> {
        if (version < serverVersion) return emptyList()
        serverVersion = version
        serverMine = mine
        needsRefresh = false
        pendingRollbackRecipeId?.let { rollbackToServer(it) }
        pendingRollbackRecipeId = null
        return replayOperations(serverMine, cart)
    }

    /**
     * 服务端确认与已知版本一致（轻量响应）：无真相变更，清新鲜度标志；
     * 若有写失败挂起的回滚，按当前（即最新已知）真相执行。
     */
    fun onUnchanged() {
        needsRefresh = false
        pendingRollbackRecipeId?.let { rollbackToServer(it) }
        pendingRollbackRecipeId = null
    }

    /** 用户勾选/取消：本地意图即时更新 */
    fun onToggle(recipeId: Long) {
        cart = cart.toggle(recipeId)
    }

    /** 用户改备注：本地意图即时更新（未选该菜忽略） */
    fun onNote(recipeId: Long, note: String) {
        cart = cart.withNote(recipeId, note)
    }

    /** 当前待写操作（差分首项；空=本地意图已与服务端一致） */
    fun pendingWrite(): DiningCartReplayOp? = replayOperations(serverMine, cart).firstOrNull()

    /** 未决操作涉及的菜品（UI 轻微区分） */
    fun pendingRecipeIds(): Set<Long> = replayOperations(serverMine, cart).map { it.recipeId }.toSet()

    /** 是否仍有未决工作（待写差分或待刷新版本） */
    fun hasPendingWork(): Boolean = needsRefresh || replayOperations(serverMine, cart).isNotEmpty()

    /** 写成功：操作已落库，但新 version 未知，须先拉取最新 */
    fun onWriteSuccess() {
        needsRefresh = true
    }

    /** 写冲突（409）：本地意图保留，待快照在新基准上重放 */
    fun onWriteConflict() {
        needsRefresh = true
    }

    /**
     * 写失败：标记需拉取最新，并挂起该菜的回滚。
     * 回滚不在失败瞬间执行——失败写不前进 version，[serverMine] 可能仍是旧真相；
     * 待 [onSnapshot] 拿到最新真相后由 [rollbackToServer] 重置本地意图。
     */
    fun onWriteFailure(recipeId: Long) {
        pendingRollbackRecipeId = recipeId
        needsRefresh = true
    }

    /** 回滚该菜本地意图到当前服务端真相（服务端有则恢复备注，无则移除勾选） */
    private fun rollbackToServer(recipeId: Long) {
        val serverNote = serverMine[recipeId]
        cart = if (serverNote != null) {
            DiningCartState(cart.selections + (recipeId to serverNote))
        } else {
            DiningCartState(cart.selections - recipeId)
        }
    }
}
