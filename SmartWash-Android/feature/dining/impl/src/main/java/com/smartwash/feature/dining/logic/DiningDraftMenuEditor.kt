package com.smartwash.feature.dining.logic

/**
 * 草稿菜单编辑状态机（纯 Kotlin，无协程/Android 依赖，JVM 单测锚点）。
 *
 * 状态 = 草稿身份（[orderId] + [joinToken]）+ 服务端真相（[serverVersion] + [serverMenu]）
 * + 未决操作（[pending]，recipeId → add/remove）。本地意图菜单由三者派生（[localMenu]）。
 *
 * 关键不变式：
 * - 惰性创建：[orderId] 未落库（0）时只维护未决操作；写操作前由调用方先创建草稿；
 * - 未决操作按「净差」记录（相对服务端真相）：用户反复切换同一道菜会相互抵消，
 *   不产生冗余写；写响应不回传新 version，成功后本地 +1；
 * - 409 冲突：服务端快照只收敛「已被他人满足」的未决操作（如他人已加同一道菜），
 *   其余未决保留，随后在新 version 基准上重放；本地意图 = 服务端真相 + 未决加 - 未决移除；
 * - 写失败（非冲突）：移除该未决操作，本地意图回滚到服务端真相 + 其余未决。
 */
class DiningDraftMenuEditor {

    /** 一次菜单写操作（相对服务端真相的净差） */
    data class Op(val recipeId: Long, val add: Boolean)

    /** 草稿订单 ID（0=未落库，写操作前需先创建） */
    var orderId: Long = 0L
        private set

    /** 加入凭证（邀请链接 token 来源；仅发起者详情下发） */
    var joinToken: String = ""
        private set

    /** 服务端最新 version（写操作携带的乐观并发基准） */
    var serverVersion: Long = 0L
        private set

    /** 服务端当前菜单（写差分基准） */
    var serverMenu: Set<Long> = emptySet()
        private set

    /** 未决操作（recipeId → add/remove）；写成功移除，服务端快照收敛已被满足项 */
    private val pending = mutableMapOf<Long, Boolean>()

    /** 草稿是否已落库（未落库时写操作需先创建） */
    val isCreated: Boolean get() = orderId > 0L

    /** 本地意图菜单 = 服务端真相 + 未决加 - 未决移除 */
    val localMenu: Set<Long>
        get() = (serverMenu + pending.filterValues { it }.keys) - pending.filterValues { !it }.keys

    /** 标记草稿已创建（惰性创建成功）：身份落库，服务端真相初始为空菜单 version=0 */
    fun markCreated(orderId: Long, joinToken: String) {
        this.orderId = orderId
        this.joinToken = joinToken
        this.serverVersion = 0L
        this.serverMenu = emptySet()
        pending.clear()
    }

    /**
     * 首载/编辑模式进入：以服务端快照覆盖真相并清空未决（无本地未决操作）。
     * 用于创建页编辑既有草稿、详情页 DRAFT 首载。
     */
    fun onInitialSnapshot(version: Long, menu: Set<Long>) {
        serverVersion = version
        serverMenu = menu
        pending.clear()
    }

    /**
     * 合并服务端快照（409 冲突刷新/轮询同步用）：收敛已被服务端满足的未决操作，
     * 其余未决保留，返回需重放的差分。
     */
    fun onServerSnapshot(version: Long, menu: Set<Long>): List<Op> {
        serverVersion = version
        serverMenu = menu
        pending.entries.removeAll { (id, add) -> (add && id in menu) || (!add && id !in menu) }
        return pendingOps()
    }

    /** 用户切换一道菜：按净差记录未决（反复切换相互抵消，不产生冗余写） */
    fun onToggle(recipeId: Long) {
        val desired = recipeId !in localMenu
        val inServer = recipeId in serverMenu
        when {
            desired == inServer -> pending.remove(recipeId)
            desired -> pending[recipeId] = true
            else -> pending[recipeId] = false
        }
    }

    /** 清空本地意图（购物车栏「清空」）：撤销未决加，对服务端全部菜记未决移除 */
    fun onClear() {
        pending.clear()
        serverMenu.forEach { pending[it] = false }
    }

    /** 当前待写操作（未决首项；空=本地意图已与服务端一致） */
    fun pendingOp(): Op? = pendingOps().firstOrNull()

    /** 全部待写操作（先加后删、各自稳定排序，供批量刷新与测试断言） */
    fun pendingOps(): List<Op> {
        val adds = pending.filterValues { it }.keys.sorted().map { Op(it, add = true) }
        val removes = pending.filterValues { !it }.keys.sorted().map { Op(it, add = false) }
        return adds + removes
    }

    /** 写成功：服务端真相前进一步（version+1 且菜单按操作落库），未决出队 */
    fun onWriteSuccess(op: Op) {
        serverVersion += 1
        serverMenu = if (op.add) serverMenu + op.recipeId else serverMenu - op.recipeId
        pending.remove(op.recipeId)
    }

    /** 写失败（非冲突）：移除该未决操作，本地意图回滚到服务端真相 + 其余未决 */
    fun onWriteFailure(op: Op) {
        pending.remove(op.recipeId)
    }
}
