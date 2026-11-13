package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享购物车同步状态机 JVM 单测（锚点）：版本冲突 → 拉取 → 重放、写失败回滚、过期快照丢弃。
 * 只断言状态行为（服务端真相 / 本地意图 / 待写差分），不涉及协程与 Android。
 */
class DiningCartSyncCoordinatorTest {

    @Test
    fun `首载后本地意图等于服务端`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(version = 3L, mine = mapOf(1L to "不要辣"))

        assertEquals(3L, c.serverVersion)
        assertEquals(mapOf(1L to "不要辣"), c.serverMine)
        assertTrue(c.cart.isSelected(1L))
        assertEquals("不要辣", c.cart.noteOf(1L))
        assertNull(c.pendingWrite())
        assertFalse(c.needsRefresh)
        assertFalse(c.hasPendingWork())
    }

    @Test
    fun `冲突后快照在新基准上重放本地意图且不动他人`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(version = 1L, mine = mapOf(1L to "旧备注"))

        // 本地意图：改备注 1 + 勾选 2
        c.onNote(1L, "新备注")
        c.onToggle(2L)

        // 写第一笔收到 409：意图保留，等待拉取
        c.onWriteConflict()
        assertTrue(c.needsRefresh)
        assertTrue(c.cart.isSelected(2L))

        // 拉取最新：version 前进；他人变更由详情承载，不进 serverMine
        val replay = c.onSnapshot(version = 5L, mine = mapOf(1L to "旧备注"))

        // 重放只含本地意图差分（改备注 + 补勾），不含任何取消操作
        assertEquals(2, replay.size)
        assertTrue(replay.contains(DiningCartReplayOp.UpdateNote(1L, "新备注")))
        assertTrue(replay.contains(DiningCartReplayOp.Add(2L, "")))
        assertTrue(replay.none { it is DiningCartReplayOp.Remove })
        // 重放涉及的菜品全部来自本地意图，绝不触碰他人记录
        assertEquals(setOf(1L, 2L), replay.map { it.recipeId }.toSet())

        assertFalse(c.needsRefresh)
        assertEquals(5L, c.serverVersion)
    }

    @Test
    fun `连续冲突每次都以最新基准重放`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, emptyMap())
        c.onToggle(1L)

        c.onWriteConflict()
        val first = c.onSnapshot(2L, emptyMap())
        assertEquals(listOf(DiningCartReplayOp.Add(1L, "")), first)

        c.onWriteConflict()
        val second = c.onSnapshot(3L, emptyMap())
        assertEquals(listOf(DiningCartReplayOp.Add(1L, "")), second)

        // 服务端确认后差分清空
        val done = c.onSnapshot(4L, mapOf(1L to ""))
        assertTrue(done.isEmpty())
        assertNull(c.pendingWrite())
    }

    @Test
    fun `补勾写失败经最新快照回滚为未选`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, emptyMap())
        c.onToggle(2L)
        assertTrue(c.cart.isSelected(2L))

        c.onWriteFailure(2L)
        assertTrue(c.needsRefresh)
        // 全量刷新：失败写不前进 version，最新真相仍无该菜
        val replay = c.onSnapshot(1L, emptyMap())

        assertTrue(replay.isEmpty())
        assertFalse(c.cart.isSelected(2L))
        assertFalse(c.needsRefresh)
    }

    @Test
    fun `取消写失败经最新快照回滚为已选并恢复备注`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, mapOf(1L to "不要辣"))
        c.onToggle(1L)
        assertFalse(c.cart.isSelected(1L))

        c.onWriteFailure(1L)
        val replay = c.onSnapshot(1L, mapOf(1L to "不要辣"))

        assertTrue(replay.isEmpty())
        assertTrue(c.cart.isSelected(1L))
        assertEquals("不要辣", c.cart.noteOf(1L))
    }

    @Test
    fun `改备注写失败经最新快照回滚旧备注`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, mapOf(1L to "旧"))
        c.onNote(1L, "新")

        c.onWriteFailure(1L)
        val replay = c.onSnapshot(1L, mapOf(1L to "旧"))

        assertTrue(replay.isEmpty())
        assertEquals("旧", c.cart.noteOf(1L))
    }

    @Test
    fun `写失败按最新真相回滚而非旧真相`() {
        val c = DiningCartSyncCoordinator()
        // 旧真相：该菜不在我名下
        c.onInitialSnapshot(1L, emptyMap())
        c.onToggle(1L)
        // 写失败（后端实际已有该菜，例如上一次写响应丢失）
        c.onWriteFailure(1L)
        // 全量刷新：版本未前进，但最新真相显示该菜已挂在我名下
        val replay = c.onSnapshot(1L, mapOf(1L to "不要辣"))

        assertTrue(replay.isEmpty())
        assertTrue(c.cart.isSelected(1L))
        assertEquals("不要辣", c.cart.noteOf(1L))
    }

    @Test
    fun `过期快照丢弃且不覆盖更新真相`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(5L, mapOf(1L to "a"))

        val replay = c.onSnapshot(3L, mapOf(1L to "b"))

        assertTrue(replay.isEmpty())
        assertEquals(5L, c.serverVersion)
        assertEquals(mapOf(1L to "a"), c.serverMine)
    }

    @Test
    fun `未决标识与同步中状态收敛`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, emptyMap())

        c.onToggle(1L)
        assertEquals(setOf(1L), c.pendingRecipeIds())
        assertTrue(c.hasPendingWork())

        c.onWriteSuccess()
        assertTrue(c.needsRefresh)
        assertTrue(c.hasPendingWork())

        // 服务端确认已含该选菜：差分清空、新鲜度恢复
        val replay = c.onSnapshot(2L, mapOf(1L to ""))
        assertTrue(replay.isEmpty())
        assertNull(c.pendingWrite())
        assertFalse(c.hasPendingWork())
    }

    @Test
    fun `轻量确认只清新鲜度不动真相`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, mapOf(1L to "a"))
        c.onWriteSuccess()

        c.onUnchanged()

        assertFalse(c.needsRefresh)
        assertEquals(1L, c.serverVersion)
        assertEquals(mapOf(1L to "a"), c.serverMine)
    }

    @Test
    fun `轻量确认执行挂起回滚`() {
        val c = DiningCartSyncCoordinator()
        c.onInitialSnapshot(1L, emptyMap())
        c.onToggle(1L)
        c.onWriteFailure(1L)

        // 服务端确认与已知版本一致：按当前已知真相回滚
        c.onUnchanged()

        assertFalse(c.cart.isSelected(1L))
        assertFalse(c.needsRefresh)
    }
}
