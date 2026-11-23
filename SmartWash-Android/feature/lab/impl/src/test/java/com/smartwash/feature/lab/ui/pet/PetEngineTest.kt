package com.smartwash.feature.lab.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 泡泡精灵状态机内核 JVM 单测（纯 Kotlin，时钟经构造注入虚拟时钟，不依赖真实时间）。
 *
 * 状态机锚点：IDLE --pet/feed/play--> HAPPY/EATING/PLAYING（限时到时回 IDLE）；
 * IDLE 超 60s 入睡；任意互动唤醒；同动作 800ms 防抖丢弃；进化阈值 10/20 次只升不降。
 */
class PetEngineTest {

    // ==================== 互动迁移 ====================

    @Test
    fun `抚摸喂食玩耍 分别迁移到对应限时状态`() {
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = 1_000L)
        assertEquals(PetState.HAPPY, engine.snapshot().state)
        assertEquals(PetAction.PET, engine.snapshot().lastAction)

        val engine2 = PetEngine()
        engine2.interact(PetAction.FEED, now = 1_000L)
        assertEquals(PetState.EATING, engine2.snapshot().state)

        val engine3 = PetEngine()
        engine3.interact(PetAction.PLAY, now = 1_000L)
        assertEquals(PetState.PLAYING, engine3.snapshot().state)
    }

    @Test
    fun `互动累计计数并推进 stateStartedAt 与 lastSeenAt`() {
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = 1_000L)
        val snap = engine.snapshot()
        assertEquals(1, snap.interactionCount)
        assertEquals(1_000L, snap.stateStartedAt)
        assertEquals(1_000L, snap.lastSeenAt)
    }

    // ==================== 防抖 ====================

    @Test
    fun `同动作窗口内重复触发被丢弃`() {
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = 1_000L)
        val result = engine.interact(PetAction.PET, now = 1_000L + PetEngine.DEBOUNCE_MS - 1)
        assertEquals(PetInteractResult.Debounced, result)
        assertEquals(1, engine.snapshot().interactionCount) // 不迁移、不计数
    }

    @Test
    fun `同动作窗口外重复触发正常接受`() {
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = 1_000L)
        val result = engine.interact(PetAction.PET, now = 1_000L + PetEngine.DEBOUNCE_MS)
        assertTrue(result is PetInteractResult.Accepted)
        assertEquals(2, engine.snapshot().interactionCount)
    }

    @Test
    fun `不同动作不受防抖影响`() {
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = 1_000L)
        val result = engine.interact(PetAction.FEED, now = 1_050L)
        assertTrue(result is PetInteractResult.Accepted)
        assertEquals(PetState.EATING, engine.snapshot().state)
        assertEquals(2, engine.snapshot().interactionCount)
    }

    // ==================== tick：限时到期与入睡 ====================

    @Test
    fun `限时状态到时自动回 IDLE`() {
        val engine = PetEngine()
        engine.interact(PetAction.PLAY, now = 0L) // PLAYING 3s
        val before = engine.tick(now = PetState.PLAYING.durationMs - 1)
        assertFalse(before.changed)

        val result = engine.tick(now = PetState.PLAYING.durationMs)
        assertTrue(result.changed)
        assertFalse(result.fellAsleep)
        assertEquals(PetState.IDLE, result.snapshot.state)
        assertNull(result.snapshot.lastAction)
    }

    @Test
    fun `IDLE 超 60 秒入睡`() {
        // 注入虚拟时钟：默认构造用 System.currentTimeMillis，会污染 tick 的 elapsed 计算
        val engine = PetEngine(clock = { 0L })
        val result = engine.tick(now = PetEngine.IDLE_SLEEP_MS)
        assertTrue(result.changed)
        assertTrue(result.fellAsleep)
        assertEquals(PetState.SLEEPING, result.snapshot.state)
    }

    @Test
    fun `IDLE 未满 60 秒不迁移`() {
        val engine = PetEngine(clock = { 0L })
        val result = engine.tick(now = PetEngine.IDLE_SLEEP_MS - 1)
        assertFalse(result.changed)
        assertEquals(PetState.IDLE, result.snapshot.state)
    }

    @Test
    fun `任意互动唤醒 SLEEPING`() {
        val engine = PetEngine(clock = { 0L })
        engine.tick(now = PetEngine.IDLE_SLEEP_MS + 1) // 入睡
        assertEquals(PetState.SLEEPING, engine.snapshot().state)
        engine.interact(PetAction.PLAY, now = PetEngine.IDLE_SLEEP_MS + 2)
        assertEquals(PetState.PLAYING, engine.snapshot().state)
    }

    // ==================== 进化阶段 ====================

    @Test
    fun `累计 10 次与 20 次触发进化且只升不降`() {
        val engine = PetEngine()
        var now = 0L
        var evolveEvents = 0
        repeat(25) {
            now += PetEngine.DEBOUNCE_MS // 错开防抖窗口
            val result = engine.interact(PetAction.PLAY, now = now)
            if (result is PetInteractResult.Accepted && result.evolved) evolveEvents++
        }
        assertEquals(PetStage.RAINBOW_BUBBLE, engine.snapshot().stage)
        // 恰好第 10 次与第 20 次各进化一次
        assertEquals(2, evolveEvents)
        // 快照计数精确累计
        assertEquals(25, engine.snapshot().interactionCount)
    }

    @Test
    fun `阶段阈值边界 forCount`() {
        assertEquals(PetStage.BUBBLE, PetStage.forCount(0))
        assertEquals(PetStage.BUBBLE, PetStage.forCount(9))
        assertEquals(PetStage.FOAM_SPIRIT, PetStage.forCount(10))
        assertEquals(PetStage.FOAM_SPIRIT, PetStage.forCount(19))
        assertEquals(PetStage.RAINBOW_BUBBLE, PetStage.forCount(20))
        assertEquals(PetStage.RAINBOW_BUBBLE, PetStage.forCount(1000))
    }

    // ==================== 放置回报档位 ====================

    @Test
    fun `从未见面判 FIRST_MEET`() {
        val engine = PetEngine()
        assertEquals(AbsenceBucket.FIRST_MEET, engine.absenceBucket(now = 100_000L))
    }

    @Test
    fun `缺勤时长分档边界`() {
        val base = 1_000_000L
        val engine = PetEngine(clock = { base })
        engine.interact(PetAction.PET, now = base) // lastSeenAt = base
        assertEquals(AbsenceBucket.JUST_NOW, engine.absenceBucket(now = base + AbsenceBucket.SHORT_MS - 1))
        assertEquals(AbsenceBucket.SHORT, engine.absenceBucket(now = base + AbsenceBucket.SHORT_MS))
        assertEquals(AbsenceBucket.LONG, engine.absenceBucket(now = base + AbsenceBucket.LONG_MS))
        assertEquals(AbsenceBucket.VERY_LONG, engine.absenceBucket(now = base + AbsenceBucket.VERY_LONG_MS))
    }

    @Test
    fun `markSeen 刷新后回 JUST_NOW`() {
        val base = 500_000L
        val engine = PetEngine()
        engine.interact(PetAction.PET, now = base)
        engine.markSeen(now = base + AbsenceBucket.VERY_LONG_MS + 1)
        assertEquals(AbsenceBucket.JUST_NOW, engine.absenceBucket(now = base + AbsenceBucket.VERY_LONG_MS + 2))
    }

    // ==================== restore：持久化恢复 ====================

    @Test
    fun `restore 恢复阶段与计数`() {
        val engine = PetEngine()
        engine.restore(PetStage.FOAM_SPIRIT, interactionCount = 12, lastSeenAt = 100_000L, now = 200_000L)
        val snap = engine.snapshot()
        assertEquals(PetStage.FOAM_SPIRIT, snap.stage)
        assertEquals(12, snap.interactionCount)
        assertEquals(100_000L, snap.lastSeenAt)
        // 隔了很久回来：独处过久继续计入入睡计时，精灵已在睡
        assertEquals(PetState.IDLE, snap.state)
        assertEquals(100_000L, snap.stateStartedAt)
        val tick = engine.tick(now = 200_000L)
        assertTrue(tick.fellAsleep)
    }

    @Test
    fun `restore 无记录时 stateStartedAt 取 now`() {
        val engine = PetEngine()
        engine.restore(PetStage.BUBBLE, interactionCount = 0, lastSeenAt = 0L, now = 900_000L)
        assertEquals(900_000L, engine.snapshot().stateStartedAt)
        // 刚恢复不应立刻入睡
        assertFalse(engine.tick(now = 900_001L).changed)
    }
}
