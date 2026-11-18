package com.smartwash.feature.dining.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 草稿菜单编辑状态机 JVM 单测（草稿协同点单批次）。
 *
 * 锚点：
 * - 惰性创建时机——未落库（orderId=0）时本地切换只记意图，写操作前需先创建；
 * - version 乐观锁——写成功 version+1、409 冲突以服务端快照刷新后重放本地意图；
 * - 写失败回滚该操作本地变更。
 */
class DiningDraftMenuEditorTest {

    // ==================== 惰性创建时机 ====================

    @Test
    fun `未创建时本地切换只记意图且不落库`() {
        val editor = DiningDraftMenuEditor()
        assertFalse(editor.isCreated)
        editor.onToggle(7L)
        assertEquals(setOf(7L), editor.localMenu)
        assertEquals(0L, editor.orderId)
        // 有待写差分：首次加菜前需先创建草稿
        assertEquals(DiningDraftMenuEditor.Op(7L, add = true), editor.pendingOp())
    }

    @Test
    fun `未创建且无切换时无待写（不触发创建）`() {
        val editor = DiningDraftMenuEditor()
        assertNull(editor.pendingOp())
    }

    @Test
    fun `创建后携带菜单则无待写差分`() {
        val editor = DiningDraftMenuEditor()
        editor.onToggle(7L)
        editor.onToggle(8L)
        val menu = editor.localMenu
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 0L, menu = menu)
        assertTrue(editor.isCreated)
        assertEquals(10L, editor.orderId)
        assertEquals("tok", editor.joinToken)
        assertNull(editor.pendingOp())
    }

    // ==================== version 写成功 ====================

    @Test
    fun `写成功后 version 递增且服务端菜单落库`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 0L, menu = emptySet())
        editor.onToggle(7L)
        val op = editor.pendingOp()!!
        editor.onWriteSuccess(op)
        assertEquals(1L, editor.serverVersion)
        assertEquals(setOf(7L), editor.serverMenu)
        assertNull(editor.pendingOp())
    }

    @Test
    fun `移除写成功 version 递增且服务端菜单收缩`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 2L, menu = setOf(7L, 8L))
        editor.onToggle(7L)
        val op = editor.pendingOp()!!
        assertEquals(DiningDraftMenuEditor.Op(7L, add = false), op)
        editor.onWriteSuccess(op)
        assertEquals(3L, editor.serverVersion)
        assertEquals(setOf(8L), editor.serverMenu)
    }

    // ==================== 409 冲突刷新重放 ====================

    @Test
    fun `冲突后服务端快照刷新 version 与菜单且保留本地意图`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 1L, menu = setOf(7L))
        // 本地加 8；服务端已被他人推进到 version=2 且加了 9
        editor.onToggle(8L)
        val replay = editor.onServerSnapshot(version = 2L, menu = setOf(7L, 9L))
        assertEquals(2L, editor.serverVersion)
        assertEquals(setOf(7L, 9L), editor.serverMenu)
        // 本地意图 = 服务端真相（含他人加的 9）+ 本地未决加 8
        assertEquals(setOf(7L, 8L, 9L), editor.localMenu)
        assertEquals(listOf(DiningDraftMenuEditor.Op(8L, add = true)), replay)
    }

    @Test
    fun `冲突刷新后重放写成功收敛到本地意图`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 1L, menu = setOf(7L))
        editor.onToggle(8L)
        editor.onServerSnapshot(version = 2L, menu = setOf(7L, 9L))
        val op = editor.pendingOp()!!
        editor.onWriteSuccess(op)
        assertEquals(3L, editor.serverVersion)
        assertEquals(setOf(7L, 9L, 8L), editor.serverMenu)
        assertNull(editor.pendingOp())
    }

    // ==================== 写失败回滚 ====================

    @Test
    fun `写失败回滚该操作本地变更`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 0L, menu = emptySet())
        editor.onToggle(7L)
        val op = editor.pendingOp()!!
        editor.onWriteFailure(op)
        assertTrue(editor.localMenu.isEmpty())
        assertNull(editor.pendingOp())
    }

    @Test
    fun `写失败回滚不影响其他本地意图`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 1L, menu = setOf(7L))
        editor.onToggle(7L) // 移除 7
        editor.onToggle(8L) // 加 8
        // 移除 7 写失败回滚 → 7 回到本地意图（服务端真相 + 未决加 8）
        editor.onWriteFailure(DiningDraftMenuEditor.Op(7L, add = false))
        assertEquals(setOf(7L, 8L), editor.localMenu)
        assertEquals(DiningDraftMenuEditor.Op(8L, add = true), editor.pendingOp())
    }

    // ==================== 差分顺序 ====================

    @Test
    fun `待写差分先加后删且各自稳定排序`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 0L, menu = setOf(3L, 4L))
        editor.onToggle(3L) // 移除 3
        editor.onToggle(4L) // 移除 4
        editor.onToggle(1L) // 加 1
        editor.onToggle(2L) // 加 2
        assertEquals(
            listOf(
                DiningDraftMenuEditor.Op(1L, add = true),
                DiningDraftMenuEditor.Op(2L, add = true),
                DiningDraftMenuEditor.Op(3L, add = false),
                DiningDraftMenuEditor.Op(4L, add = false),
            ),
            editor.pendingOps(),
        )
    }

    @Test
    fun `清空本地意图产生全部移除差分`() {
        val editor = DiningDraftMenuEditor()
        editor.markCreated(orderId = 10L, joinToken = "tok")
        editor.onInitialSnapshot(version = 5L, menu = setOf(7L, 8L))
        editor.onClear()
        assertEquals(
            listOf(
                DiningDraftMenuEditor.Op(7L, add = false),
                DiningDraftMenuEditor.Op(8L, add = false),
            ),
            editor.pendingOps(),
        )
    }
}
